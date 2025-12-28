package link.e4mc.iroh;

import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("nyaa");
        try (var endpoint = new Endpoint(new byte[][]{"meower".getBytes(StandardCharsets.UTF_8)})) {
            endpoint.waitOnline().thenAccept(addr -> {
                System.out.println(addr);
            });
            endpoint.accept().thenAccept(conn -> {
                System.out.println(conn.peerAddress());
                conn.acceptBi().thenAccept(stream -> {
                    stream.readIrohStreamByteArray(10000).thenAccept(bytes -> {
                        System.out.println(new String(bytes, StandardCharsets.UTF_8));
                    });
                });
            });
            while (true) {
                var polled = endpoint.pollCallbackLoop();
                System.out.println(polled);
                polled.run();
            }
        }
    }
}