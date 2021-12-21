/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;

/** @deprecated to be removed >= 1.4. There will be no replacement for this exporter */
@Deprecated(since = "1.3", forRemoval = true)
public final class DebugHttpServer {

  private static final Charset CHARSET = Charsets.UTF_8;

  private final int maxSize;
  private final LinkedList<String> records;
  private HttpServer server;

  public DebugHttpServer(final int port, final int maxSize) {
    this.maxSize = maxSize;
    server = startHttpServer(port);
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

  public synchronized void add(final Record record) throws JsonProcessingException {
    while (records.size() >= maxSize) {
      records.removeLast();
    }

    records.addFirst(record.toJson());
  }

  class RequestHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange httpExchange) throws IOException {
      final String path = httpExchange.getRequestURI().getPath().substring(1);
      if ("records.json".equals(path)) {
        final byte[] response = getRecords();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.sendResponseHeaders(200, response.length);
        try (final OutputStream outputStream = httpExchange.getResponseBody()) {
          outputStream.write(response);
        }
      } else {
        // redirect to record.json
        httpExchange.getResponseHeaders().add("Location", "/records.json");
        httpExchange.sendResponseHeaders(302, -1);
      }
    }

    private byte[] getRecords() {
      final String json = "[" + String.join(",", records) + "]";
      return json.getBytes(CHARSET);
    }
  }
}
