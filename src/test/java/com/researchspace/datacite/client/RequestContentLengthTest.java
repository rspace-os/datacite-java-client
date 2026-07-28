package com.researchspace.datacite.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.datacite.model.DataCiteDoi;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DataCite rejects requests without a Content-Length header, so JSON bodies must not be
 * sent chunked. This test captures the request on a real socket because
 * MockRestServiceServer-based tests never reach the HTTP transport, which is where
 * the framing is decided.
 */
public class RequestContentLengthTest {

    @Test
    public void doiRegistrationPostIsSentWithContentLength() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(10_000);
            List<String> requestHeaders = new ArrayList<>();
            Thread serverThread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        requestHeaders.add(line);
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: 2\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "{}").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            serverThread.start();

            DataCiteClientImpl client = new DataCiteClientImpl(
                new URI("http://127.0.0.1:" + server.getLocalPort() + "/"),
                "user", "password", "10.12345");
            client.registerDoi(new DataCiteDoi());
            serverThread.join(10_000);

            assertTrue(
                requestHeaders.stream().anyMatch(h -> h.toLowerCase().startsWith("content-length:")),
                "expected a Content-Length header, got: " + requestHeaders);
            assertFalse(
                requestHeaders.stream().anyMatch(h -> h.toLowerCase().startsWith("transfer-encoding:")),
                "expected no Transfer-Encoding header, got: " + requestHeaders);
        }
    }
}
