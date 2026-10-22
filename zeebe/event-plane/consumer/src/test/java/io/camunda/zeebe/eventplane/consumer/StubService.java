/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for the Event Plane's service on a free port: it answers the GETs that a test sets up,
 * and 404 to the others, and notes what it was asked.
 */
final class StubService implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpServer server;
  private final Map<String, Response> responses = new ConcurrentHashMap<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();

  private StubService(final HttpServer server) {
    this.server = server;
  }

  static StubService start() throws IOException {
    final var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final var stub = new StubService(server);
    server.createContext("/", stub::answer);
    server.start();
    return stub;
  }

  /** Answers a GET of the path and query, as the request has it, with the body. */
  StubService respond(final String pathAndQuery, final int status, final String body) {
    responses.put(pathAndQuery, new Response(status, body));
    return this;
  }

  /**
   * Answers as a service with these clusters does, each with the JSON of its store settings: the
   * list, and the credentials of each cluster.
   */
  StubService clusters(final Map<String, String> storeJsonByCluster) throws IOException {
    final var listed = MAPPER.createObjectNode();
    final var clusters = listed.putObject("clusters");
    for (final var cluster : storeJsonByCluster.entrySet()) {
      clusters.putObject(cluster.getKey());
      respond(
          "/v1/credentials?cluster=" + URLEncoder.encode(cluster.getKey(), StandardCharsets.UTF_8),
          200,
          cluster.getValue());
    }
    return respond("/v1/clusters", 200, MAPPER.writeValueAsString(listed));
  }

  /** The service's URI, without a trailing slash. */
  URI uri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  /** The path and query of every request so far. */
  List<String> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void answer(final HttpExchange exchange) throws IOException {
    final var query = exchange.getRequestURI().getRawQuery();
    final var request = exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
    requests.add(request);
    final var response =
        responses.getOrDefault(request, new Response(404, "{\"error\":\"no such request\"}"));
    final var body = response.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(response.status(), body.length);
    try (final var out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private record Response(int status, String body) {}
}
