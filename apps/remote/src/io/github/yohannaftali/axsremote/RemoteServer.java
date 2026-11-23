package io.github.yohannaftali.axsremote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HTTP + WebSocket server for the phone remote at http://<TV IP>:8077, running
 * while AXS Remote is the TV's keyboard. No pairing (owner's choice): any
 * device on the home network may connect. Plain HTTP because iOS Safari does
 * not let a page's WebSocket use a self-signed certificate. A WebSocket from
 * another site's page (Origin not this TV) is refused. Commands are a fixed
 * set (keys, volume, text); there is no shell, file or install access.
 * Nothing is logged.
 */
final class RemoteServer {
    static final int PORT = 8077;
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
    private ServerSocket server;
    private Listener listener;
    private String lastEditor = "editor:0";

    private RemoteServer(Context c) {
        app = c.getApplicationContext();
    }

    static synchronized RemoteServer get(Context c) {
        if (instance == null) instance = new RemoteServer(c);
        return instance;
    }

    /** Starts the server if it is not running (bound to every interface, so an IP change needs no restart). */
    synchronized void ensureRunning() {
        if (server != null && !server.isClosed()) return;
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new java.net.InetSocketAddress(PORT), 16);
            server = s;
            Thread t = new Thread(() -> acceptLoop(s), "remote-accept");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            server = null;
        }
    }

    synchronized void setListener(Listener l) {
        listener = l;
        if (l != null) notifyPhones();
    }

    synchronized boolean running() {
        return server != null && !server.isClosed();
    }

    /** The address phones open, or null when the TV has no IPv4 network. Read at runtime, shown only on screen. */
    String url() {
        InetAddress ip = lanAddress(app);
        return ip == null ? null : "http://" + ip.getHostAddress() + ":" + PORT + "/";
    }

    static InetAddress lanAddress(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress a : lp.getLinkAddresses()) {
            InetAddress ip = a.getAddress();
            if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) return ip;
        }
        return null;
    }

    synchronized void close() {
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

    private void acceptLoop(ServerSocket s) {
        while (!s.isClosed()) {
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
            String origin = null;
            String host = null;
            boolean upgrade = false;
            for (String l : lines) {
                int i = l.indexOf(':');
                if (i < 0) continue;
                String name = l.substring(0, i).trim().toLowerCase(Locale.ROOT);
                String value = l.substring(i + 1).trim();
                if (name.equals("sec-websocket-key")) key = value;
                if (name.equals("upgrade") && value.equalsIgnoreCase("websocket")) upgrade = true;
                if (name.equals("origin")) origin = value;
                if (name.equals("host")) host = value;
            }
            if (path.startsWith("/ws")) {
                // No pairing, so stop other web pages the phone visits from driving the TV.
                boolean sameSite = origin == null || (host != null && origin.equalsIgnoreCase("http://" + host));
                if (!upgrade || key == null || !sameSite) {
                    respond(out, 403, "text/plain", "Open the remote from the TV's address.".getBytes(StandardCharsets.UTF_8));
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
