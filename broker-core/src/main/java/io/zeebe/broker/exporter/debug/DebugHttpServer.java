/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Charsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.util.StreamUtil;
import io.zeebe.util.collection.Tuple;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class DebugHttpServer {

  private static final Charset CHARSET = Charsets.UTF_8;
  private static final String[] RESOURCE_NAMES =
      new String[] {
        "index.html",
        "bootstrap-4.1.3.min.css",
        "bootstrap-4.1.3.min.js",
        "jquery-3.3.1.slim.min.js",
        "mustache-3.0.0.min.js"
      };

  private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

  static {
    CONTENT_TYPES.put(".css", "text/css");
    CONTENT_TYPES.put(".html", "text/html");
    CONTENT_TYPES.put(".js", "text/javascript");
    CONTENT_TYPES.put(".json", "application/json");
  }

  private final int maxSize;
  private HttpServer server;
  private final Map<String, byte[]> resources;
  private ObjectMapper objectMapper;
  private final LinkedList<String> records;

  public DebugHttpServer(int port, int maxSize) {
    this.maxSize = maxSize;
    server = startHttpServer(port);
    resources = loadResources();
    objectMapper = createObjectMapper();
    records = new LinkedList<>();
  }

  public void close() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private HttpServer startHttpServer(int port) {
    try {
      final HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
      httpServer.createContext("/", new RequestHandler());
      httpServer.start();
      return server;
    } catch (IOException e) {
      throw new RuntimeException("Failed to start debug exporter http server", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, byte[]> loadResources() {
    return Arrays.stream(RESOURCE_NAMES)
        .map(resourceName -> new Tuple<>(resourceName, loadResource(resourceName)))
        .collect(Collectors.toConcurrentMap(Tuple::getLeft, Tuple::getRight));
  }

  private byte[] loadResource(String resourceName) {
    try (InputStream resourceAsStream = DebugHttpServer.class.getResourceAsStream(resourceName)) {
      if (resourceAsStream != null) {
        return StreamUtil.read(resourceAsStream);
      } else {
        throw new RuntimeException(
            "Failed to find resource " + resourceName + " for debug http exporter");
      }
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to read resource " + resourceName + " for debug http exporter", e);
    }
  }

  private ObjectMapper createObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper;
  }

  public synchronized void add(Record record) throws JsonProcessingException {
    while (records.size() >= maxSize) {
      records.removeLast();
    }

    records.addFirst(objectMapper.writeValueAsString(record));
  }

  class RequestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      String path = httpExchange.getRequestURI().getPath().substring(1);
      if (path.isEmpty()) {
        path = "index.html";
      }

      final String extension = path.substring(path.lastIndexOf('.'));
      final String contentType = CONTENT_TYPES.get(extension);
      if (contentType != null) {
        httpExchange.getResponseHeaders().add("Content-Type", contentType);
      }

      final byte[] response;
      if ("records.json".equals(path)) {
        response = getRecords();
      } else {
        response = resources.get(path);
      }

      if (response.length > 0) {
        httpExchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = httpExchange.getResponseBody()) {
          outputStream.write(response);
        }
      } else {
        httpExchange.sendResponseHeaders(404, 0);
      }
    }

    private byte[] getRecords() {
      final String json = "[" + String.join(",", records) + "]";
      return json.getBytes(CHARSET);
    }
  }
}
