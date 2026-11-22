package io.github.yohannaftali.axsremote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * HTTPS + WebSocket server for the phone remote, port 8077. Serves one page
 * and accepts a WebSocket only with the current pairing token (from the QR
 * code). Commands are a fixed set (keys, volume, text); there is no shell,
 * file or install access. Runs only on demand and stops after 10 minutes
 * without a command or a connected phone. Nothing is logged.
 */
final class RemoteServer {
    static final int PORT = 8077;
    private static final long IDLE_MS = 10 * 60_000L;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC11B05";
    private static RemoteServer instance;

    interface Listener {
        void onPhonesChanged(int count);
    }

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Peer> peers = new ArrayList<>();
    /** Network writes are not allowed on the main thread, where the keyboard's callbacks run. */
    private final java.util.concurrent.ExecutorService sender = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile String token = "";
    private volatile long lastUse;
    private ServerSocket server;
    private SelfSignedCert cert;
    private InetAddress address;
    private Listener listener;
    private String lastEditor = "editor:0";

    private RemoteServer(Context c) {
        app = c.getApplicationContext();
    }

    static synchronized RemoteServer get(Context c) {
        if (instance == null) instance = new RemoteServer(c);
        return instance;
    }

    /** Starts (or keeps) the server for this address and issues a new token; returns the URL for the QR code. */
    synchronized String open(InetAddress ip, Listener l) throws Exception {
        listener = l;
        token = newToken();
        lastUse = System.currentTimeMillis();
        if (server != null && ip.equals(address)) {
            dropPeers();
            return url();
        }
        close();
        address = ip;
        cert = SelfSignedCert.forAddress(app, ip);
        SSLServerSocket s = (SSLServerSocket) cert.serverSocketFactory().createServerSocket();
        s.setReuseAddress(true);
        s.bind(new java.net.InetSocketAddress(PORT), 8);
        server = s;
        Thread t = new Thread(this::acceptLoop, "remote-accept");
        t.setDaemon(true);
        t.start();
        ui.postDelayed(idleCheck, 60_000);
        return url();
    }

    synchronized void setListener(Listener l) {
        listener = l;
    }

    String fingerprint() {
        return cert == null ? "" : cert.shortFingerprint();
    }

    synchronized boolean running() {
        return server != null;
    }

    private String url() {
        return "https://" + address.getHostAddress() + ":" + PORT + "/#" + token;
    }

    private static String newToken() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    synchronized void close() {
        ui.removeCallbacks(idleCheck);
        dropPeers();
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Already closed.
            }
            server = null;
        }
    }

    private void dropPeers() {
        List<Peer> copy;
        synchronized (peers) {
            copy = new ArrayList<>(peers);
            peers.clear();
        }
        for (Peer p : copy) p.close();
        notifyPhones();
    }

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            boolean idle;
            synchronized (peers) {
                idle = peers.isEmpty() && System.currentTimeMillis() - lastUse > IDLE_MS;
            }
            if (idle) close(); else ui.postDelayed(this, 60_000);
        }
    };

    /** Called by the keyboard when the focused field changes; the phone shows or hides its text bar. */
    void editorChanged(boolean editing, String hint, String text) {
        String msg = editing ? "editor:1:" + clean(hint) + ":" + (text == null ? "" : text) : "editor:0";
        lastEditor = msg;
        broadcast(msg);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(':', ' ').replace('\n', ' ');
    }

    private void broadcast(String msg) {
        List<Peer> copy;
        synchronized (peers) {
            copy = new ArrayList<>(peers);
        }
        if (copy.isEmpty()) return;
        sender.execute(() -> {
            for (Peer p : copy) p.send(msg);
        });
    }

    private void notifyPhones() {
        int n;
        synchronized (peers) {
            n = peers.size();
        }
        ui.post(() -> {
            if (listener != null) listener.onPhonesChanged(n);
        });
    }

    // ---- HTTP ----

    private void acceptLoop() {
        ServerSocket s = server;
        while (s != null && !s.isClosed()) {
            try {
                Socket c = s.accept();
                Thread t = new Thread(() -> handle(c), "remote-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (s.isClosed()) return;
            }
        }
    }

    private void handle(Socket c) {
        try {
            c.setSoTimeout(15_000);
            if (c instanceof SSLSocket) ((SSLSocket) c).startHandshake();
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            String head = readHead(in);
            if (head == null) {
                c.close();
                return;
            }
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2 || !req[0].equals("GET")) {
                respond(out, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                c.close();
                return;
            }
            String path = req[1];
            String key = null;
            boolean upgrade = false;
            for (String l : lines) {
                int i = l.indexOf(':');
                if (i < 0) continue;
                String name = l.substring(0, i).trim().toLowerCase(Locale.ROOT);
                String value = l.substring(i + 1).trim();
                if (name.equals("sec-websocket-key")) key = value;
                if (name.equals("upgrade") && value.equalsIgnoreCase("websocket")) upgrade = true;
            }
            if (path.startsWith("/ws")) {
                String given = query(path, "t");
                if (!upgrade || key == null || token.isEmpty() || !token.equals(given)) {
                    respond(out, 403, "text/plain", "Scan the QR code on the TV again.".getBytes(StandardCharsets.UTF_8));
                    c.close();
                    return;
                }
                String accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                c.setSoTimeout(0);
                Peer p = new Peer(c, in, out);
                synchronized (peers) {
                    peers.add(p);
                }
                lastUse = System.currentTimeMillis();
                notifyPhones();
                p.send("hello:" + (RemoteIme.active() ? "1" : "0"));
                p.send(lastEditor);
                p.loop();
                synchronized (peers) {
                    peers.remove(p);
                }
                notifyPhones();
                return;
            }
            if (path.equals("/") || path.startsWith("/?") || path.startsWith("/index.html")) {
                respond(out, 200, "text/html; charset=utf-8", asset("remote.html"));
            } else {
                respond(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
            c.close();
        } catch (Exception e) {
            try {
                c.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
        }
    }

    private static String query(String path, String name) {
        int q = path.indexOf('?');
        if (q < 0) return "";
        for (String kv : path.substring(q + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(name)) {
                try {
                    return URLDecoder.decode(kv.substring(e + 1), "UTF-8");
                } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ex) {
                    return "";
                }
            }
        }
        return "";
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (b.size() < 8192) {
            int ch = in.read();
            if (ch < 0) return null;
            b.write(ch);
            if ((state == 0 || state == 2) && ch == '\r') state++;
            else if ((state == 1 || state == 3) && ch == '\n') state++;
            else state = 0;
            if (state == 4) return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private static void respond(OutputStream out, int code, String type, byte[] body) throws IOException {
        String reason = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Error";
        out.write(("HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private byte[] asset(String name) throws IOException {
        try (InputStream in = app.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    // ---- WebSocket ----

    private final class Peer {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        Peer(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void loop() {
            try {
                while (true) {
                    int b0 = in.read();
                    int b1 = in.read();
                    if (b0 < 0 || b1 < 0) return;
                    int opcode = b0 & 0x0F;
                    long len = b1 & 0x7F;
                    if (len == 126) len = (in.read() << 8) | in.read();
                    else if (len == 127) return;
                    if (len > 4096 || (b1 & 0x80) == 0) return;
                    byte[] mask = new byte[4];
                    readFully(mask);
                    byte[] data = new byte[(int) len];
                    readFully(data);
                    for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
                    if (opcode == 0x8) return;
                    if (opcode == 0x9) {
                        frame(0xA, data);
                        continue;
                    }
                    if (opcode == 0x1) {
                        lastUse = System.currentTimeMillis();
                        String msg = new String(data, StandardCharsets.UTF_8);
                        if (msg.equals("ping")) send("pong");
                        else ui.post(() -> RemoteIme.command(app, msg));
                    }
                }
            } catch (IOException ignored) {
                // Phone went away.
            } finally {
                close();
            }
        }

        private void readFully(byte[] b) throws IOException {
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) throw new SocketException("closed");
                off += n;
            }
        }

        synchronized void send(String msg) {
            try {
                frame(0x1, msg.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                close();
            }
        }

        private synchronized void frame(int opcode, byte[] data) throws IOException {
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(0x80 | opcode);
            if (data.length < 126) {
                f.write(data.length);
            } else {
                f.write(126);
                f.write(data.length >>> 8);
                f.write(data.length & 0xFF);
            }
            f.write(data, 0, data.length);
            out.write(f.toByteArray());
            out.flush();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }
}
