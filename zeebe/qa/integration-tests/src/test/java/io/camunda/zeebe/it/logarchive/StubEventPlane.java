/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A stand-in for the event plane that implements the commit entry endpoint of spec section 19.1:
 * per (cluster, tenant, partition, timeline) it applies an entry only if it is the next expected
 * one, skips overlapping entries, and answers {@code 409} on a gap. It records every request, so
 * that tests can assert on what the cluster announced.
 */
final class StubEventPlane implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final Map<TimelineKey, List<JsonNode>> applied = new HashMap<>();
  private final List<Request> requests = new ArrayList<>();
  private volatile boolean down;

  StubEventPlane() {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    server.setExecutor(executor);
    server.createContext("/v1/log-archive/", this::handle);
    server.start();
  }

  String url() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  /** While down, every request is answered with {@code 503} and nothing is applied. */
  void setDown(final boolean down) {
    this.down = down;
  }

  /** The entries applied to a timeline, in order; empty if the timeline is unknown. */
  synchronized List<JsonNode> entries(final TimelineKey key) {
    return List.copyOf(applied.getOrDefault(key, List.of()));
  }

  /** The seq the stub expects next on a timeline, 1 if the timeline is unknown. */
  synchronized long nextSeq(final TimelineKey key) {
    return applied.getOrDefault(key, List.of()).size() + 1L;
  }

  /** Forgets everything about a timeline, as if the event plane's database was restored empty. */
  synchronized void reset(final TimelineKey key) {
    applied.remove(key);
  }

  synchronized List<Request> requests() {
    return List.copyOf(requests);
  }

  synchronized List<TimelineKey> timelines() {
    return List.copyOf(applied.keySet());
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(final HttpExchange exchange) throws IOException {
    try (exchange) {
      final byte[] body = exchange.getRequestBody().readAllBytes();
      if (down) {
        respond(exchange, 503, "{}");
        return;
      }
      final var key = parsePath(exchange.getRequestURI().getRawPath());
      if (key == null || !"POST".equals(exchange.getRequestMethod())) {
        respond(exchange, 404, "{}");
        return;
      }
      final List<JsonNode> entries;
      try {
        entries = parseEntries(key, body);
      } catch (final IllegalArgumentException | IOException e) {
        record(key, -1, 0, 400);
        respond(
            exchange,
            400,
            "{\"detail\":\"" + String.valueOf(e.getMessage()).replace('"', '\'') + "\"}");
        return;
      }
      final long firstSeq = entries.getFirst().get("seq").asLong();
      final long nextSeq;
      final int status;
      synchronized (this) {
        final var list = applied.computeIfAbsent(key, k -> new ArrayList<>());
        final long expected = list.size() + 1L;
        if (firstSeq > expected) {
          status = 409;
        } else {
          status = 200;
          for (final var entry : entries) {
            if (entry.get("seq").asLong() == list.size() + 1L) {
              list.add(entry);
            }
          }
        }
        nextSeq = list.size() + 1L;
        if (list.isEmpty()) {
          applied.remove(key);
        }
        requests.add(new Request(key, firstSeq, entries.size(), status, nextSeq));
      }
      respond(exchange, status, "{\"nextSeq\":" + nextSeq + "}");
    }
  }

  private synchronized void record(
      final TimelineKey key, final long firstSeq, final int count, final int status) {
    requests.add(new Request(key, firstSeq, count, status, -1));
  }

  private static List<JsonNode> parseEntries(final TimelineKey key, final byte[] body)
      throws IOException {
    final JsonNode root = MAPPER.readTree(body);
    final JsonNode array = root == null ? null : root.get("entries");
    if (array == null || !array.isArray() || array.isEmpty()) {
      throw new IllegalArgumentException("no entries");
    }
    final var entries = new ArrayList<JsonNode>();
    array.forEach(entries::add);
    for (int i = 0; i < entries.size(); i++) {
      final var entry = entries.get(i);
      if (!entry.hasNonNull("seq")
          || entry.get("seq").asLong() != entries.getFirst().get("seq").asLong() + i) {
        throw new IllegalArgumentException("entries are not consecutive");
      }
      if (!key.timelineId().equals(entry.path("timelineId").asText())
          || key.partitionId() != entry.path("partitionId").asInt(-1)) {
        throw new IllegalArgumentException("entry does not belong to the path");
      }
    }
    final var first = entries.getFirst();
    if (first.get("seq").asLong() == 1 && !"TIMELINE_STARTED".equals(first.path("type").asText())) {
      throw new IllegalArgumentException("seq 1 is not TIMELINE_STARTED");
    }
    return entries;
  }

  private static TimelineKey parsePath(final String rawPath) {
    final var s = rawPath.split("/");
    // "", v1, log-archive, cluster, tenant, partitions, <id>, timelines, <id>, commits
    if (s.length != 10
        || !"partitions".equals(s[5])
        || !"timelines".equals(s[7])
        || !"commits".equals(s[9])) {
      return null;
    }
    try {
      return new TimelineKey(decode(s[3]), decode(s[4]), Integer.parseInt(s[6]), decode(s[8]));
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static String decode(final String segment) {
    return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private static void respond(final HttpExchange exchange, final int status, final String body)
      throws IOException {
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  record TimelineKey(String clusterId, String tenantId, int partitionId, String timelineId) {}

  /** One request as the stub answered it; {@code firstSeq} is -1 if it was malformed. */
  record Request(TimelineKey key, long firstSeq, int count, int status, long nextSeq) {}
}
