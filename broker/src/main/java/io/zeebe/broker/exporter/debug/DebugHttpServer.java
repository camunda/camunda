/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.zeebe.protocol.record.Record;
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

public final class DebugHttpServer {

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
  private final Map<String, byte[]> resources;
  private final LinkedList<String> records;
  private HttpServer server;

  public DebugHttpServer(final int port, final int maxSize) {
    this.maxSize = maxSize;
    server = startHttpServer(port);
    resources = loadResources();
    records = new LinkedList<>();
  }

  public void close() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private HttpServer startHttpServer(final int port) {
    try {
      final HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
      httpServer.createContext("/", new RequestHandler());
      httpServer.start();
      return server;
    } catch (final IOException e) {
      throw new RuntimeException("Failed to start debug exporter http server", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, byte[]> loadResources() {
    return Arrays.stream(RESOURCE_NAMES)
        .map(resourceName -> new Tuple<>(resourceName, loadResource(resourceName)))
        .collect(Collectors.toConcurrentMap(Tuple::getLeft, Tuple::getRight));
  }

  private byte[] loadResource(final String resourceName) {
    try (final InputStream resourceAsStream =
        DebugHttpServer.class.getResourceAsStream(resourceName)) {
      if (resourceAsStream != null) {
        return resourceAsStream.readAllBytes();
      } else {
        throw new RuntimeException(
            "Failed to find resource " + resourceName + " for debug http exporter");
      }
    } catch (final IOException e) {
      throw new RuntimeException(
          "Failed to read resource " + resourceName + " for debug http exporter", e);
    }
  }

  public synchronized void add(final Record record) throws JsonProcessingException {
    while (records.size() >= maxSize) {
      records.removeLast();
    }

    records.addFirst(record.toJson());
  }

  class RequestHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange httpExchange) throws IOException {
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
        try (final OutputStream outputStream = httpExchange.getResponseBody()) {
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
