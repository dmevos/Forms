package ru.osipov;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Request {
    private final String method;
    private final String path;
    private final String protocolVersion;
    private final List<String> headers;
    private final byte[] body;
    private final ConcurrentHashMap<String, String> queryParams;

    private Request(String method, String path, String protocolVersion, List<String> headers, byte[] body,
                    ConcurrentHashMap<String, String> queryParams) {
        this.method = method;
        this.path = path;
        this.protocolVersion = protocolVersion;
        this.headers = headers;
        this.body = body;
        this.queryParams = queryParams;
    }

    public static Request parse(BufferedInputStream in, BufferedOutputStream out) throws IOException {

        // лимит на request line + заголовки
        final var limit = 4096;

        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // ищем request line ----------------------------------------------------------
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            badRequest(out);
            return null;
        }

        // читаем request line --------------------------------------------------------
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            badRequest(out);
            return null;
        }

        final var method = requestLine[0];
        String path;
        String paramString;
        if (requestLine[1].contains("?")) {
            path = requestLine[1].split("\\?")[0];
            paramString = requestLine[1].split("\\?")[1];
        } else {
            path = requestLine[1];
            paramString = null;
        }

        if (!path.startsWith("/")) {
            badRequest(out);
            return null;
        }

        ConcurrentHashMap<String, String> queryParams = new ConcurrentHashMap<>();
        List<NameValuePair> args = URLEncodedUtils.parse(paramString, Charset.defaultCharset());
        for (NameValuePair arg : args) {
            queryParams.put(arg.getName(), arg.getValue());
        }

        // ищем заголовки -----------------------------------------------------------------
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            badRequest(out);
            return null;
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

        // для GET тела нет
        byte[] body = null;
        if (!method.equals("GET")) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                body = in.readNBytes(length);
            }
        }

        return new Request(method, path, requestLine[2], headers, body, queryParams);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public ConcurrentHashMap<String, String> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String paramName) {
        if (queryParams.containsKey(paramName))
            return queryParams.get(paramName);
        return null;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", protocolVersion='" + protocolVersion + '\'' +
                ", headers=" + headers +
                ", body=" + Arrays.toString(body) +
                ", queryParams=" + queryParams +
                '}';
    }
}
