package io.github.yohannaftali.axsremote;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * The TV's own self-signed TLS certificate, made on the TV the first time and
 * whenever its address changes or it nears expiry. Hand-built X.509 v3 so it
 * meets what iOS asks of server certificates: the TV's IP as subjectAltName,
 * extendedKeyUsage serverAuth, at most 825 days valid. The private key lives
 * only in the app's private files; nothing is ever written to the repo.
 */
final class SelfSignedCert {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long VALID = 397 * DAY;
    private static final char[] PASS = "axsremote".toCharArray();

    final X509Certificate cert;
    final PrivateKey key;

    private SelfSignedCert(X509Certificate cert, PrivateKey key) {
        this.cert = cert;
        this.key = key;
    }

    /** Loads the stored certificate, or makes a new one if missing, expiring, or for another address. */
    static SelfSignedCert forAddress(Context c, InetAddress ip) throws GeneralSecurityException, IOException {
        File keyFile = new File(c.getFilesDir(), "tls.key");
        File certFile = new File(c.getFilesDir(), "tls.crt");
        if (keyFile.isFile() && certFile.isFile()) {
            try {
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(read(certFile)));
                PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(read(keyFile)));
                boolean fresh = cert.getNotAfter().getTime() - System.currentTimeMillis() > 30 * DAY;
                if (fresh && coversIp(cert, ip)) return new SelfSignedCert(cert, key);
            } catch (GeneralSecurityException | IOException ignored) {
                // Unreadable: make a new one below.
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair pair = g.generateKeyPair();
        byte[] der = build(pair, ip);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        write(keyFile, pair.getPrivate().getEncoded());
        write(certFile, der);
        return new SelfSignedCert(cert, pair.getPrivate());
    }

    SSLServerSocketFactory serverSocketFactory() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("axsremote", key, PASS, new Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASS);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx.getServerSocketFactory();
    }

    /** SHA-256 of the certificate, as browsers show it ("AB:CD:…"), first 8 bytes. */
    String shortFingerprint() {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) b.append(':');
                b.append(String.format(Locale.ROOT, "%02X", h[i]));
            }
            return b.toString();
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    private static boolean coversIp(X509Certificate cert, InetAddress ip) {
        try {
            if (cert.getSubjectAlternativeNames() == null) return false;
            for (java.util.List<?> san : cert.getSubjectAlternativeNames()) {
                if (Integer.valueOf(7).equals(san.get(0)) && ip.getHostAddress().equals(san.get(1))) return true;
            }
        } catch (GeneralSecurityException ignored) {
            // Treat as not covering.
        }
        return false;
    }

    // ---- DER ----

    private static byte[] build(KeyPair pair, InetAddress ip) throws GeneralSecurityException, IOException {
        byte[] ecdsaSha256 = seq(oid(1, 2, 840, 10045, 4, 3, 2));
        byte[] name = seq(set(seq(oid(2, 5, 4, 3), tlv(0x0C, "AXS Remote".getBytes("UTF-8")))));
        long now = System.currentTimeMillis();
        byte[] validity = seq(time(new Date(now - DAY)), time(new Date(now + VALID)));
        byte[] serial = new byte[16];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F;
        byte[] san = seq(oid(2, 5, 29, 17), tlv(0x04, seq(tlv(0x87, ip.getAddress()))));
        byte[] eku = seq(oid(2, 5, 29, 37), tlv(0x04, seq(oid(1, 3, 6, 1, 5, 5, 7, 3, 1))));
        byte[] basic = seq(oid(2, 5, 29, 19), tlv(0x04, seq()));
        byte[] tbs = seq(
                tlv(0xA0, tlv(0x02, new byte[]{2})),
                tlv(0x02, new BigInteger(1, serial).toByteArray()),
                ecdsaSha256,
                name,
                validity,
                name,
                pair.getPublic().getEncoded(),
                tlv(0xA3, seq(san, eku, basic)));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(pair.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        return seq(tbs, ecdsaSha256, tlv(0x03, bits));
    }

    private static byte[] time(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x17, f.format(d).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int v = arcs[i];
            int n = 0;
            byte[] tmp = new byte[5];
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>>= 7;
            } while (v != 0);
            for (int j = n - 1; j >= 0; j--) out.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, out.toByteArray());
    }

    private static byte[] seq(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = value.length;
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x100) {
            out.write(0x81);
            out.write(len);
        } else {
            out.write(0x82);
            out.write(len >>> 8);
            out.write(len & 0xFF);
        }
        out.write(value, 0, value.length);
        return out.toByteArray();
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }
}
