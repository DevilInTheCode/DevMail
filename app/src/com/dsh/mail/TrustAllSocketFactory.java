package com.dsh.mail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSL-фабрика, которая принимает любой сертификат.
 * Нужна для почтовых серверов с самоподписанными/корпоративными сертификатами
 * (частая причина "Trust anchor for certification path not found").
 * Включается только галочкой в настройках аккаунта.
 */
public class TrustAllSocketFactory extends SSLSocketFactory {

    private static SSLSocketFactory base;

    private static synchronized SSLSocketFactory base() {
        if (base == null) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, new SecureRandom());
                base = ctx.getSocketFactory();
            } catch (Exception e) {
                base = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
        }
        return base;
    }

    /** JavaMail ищет этот статический метод, создавая фабрику по имени класса. */
    public static SocketFactory getDefault() {
        return new TrustAllSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return base().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return base().getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return base().createSocket();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return base().createSocket(s, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return base().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return base().createSocket(host, port, local, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return base().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress local, int localPort)
            throws IOException {
        return base().createSocket(address, port, local, localPort);
    }
}
