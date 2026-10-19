/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubFanoutPullerTest {

  private static final String TYPE = "test.v1";

  private final Map<String, Page> pagesByAfter = new ConcurrentHashMap<>();
  private final Map<String, byte[]> payloads = new ConcurrentHashMap<>();
  private final List<String> requestedAfter = new ArrayList<>();
  private final List<JsonNode> handled = new ArrayList<>();
  private final AtomicInteger failuresLeft = new AtomicInteger();
  private HttpServer server;
  private HubFanoutPuller puller;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/hub/api/fanout/events",
        exchange -> {
          final var after = query(exchange.getRequestURI(), "after");
          requestedAfter.add(after);
          final var page = pagesByAfter.getOrDefault(after, new Page("[]", false));
          exchange.getResponseHeaders().add("Fanout-Has-More", Boolean.toString(page.hasMore()));
          final byte[] body = page.body().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/hub/api/fanout/payloads",
        exchange -> {
          final byte[] body = payloads.get(exchange.getRequestURI().getPath());
          exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
          if (body != null) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();

    final var properties =
        new HubFanoutProperties(
            true,
            URI.create("http://localhost:" + server.getAddress().getPort() + "/hub"),
            "oc-1",
            0,
            Duration.ZERO,
            3,
            "logging");
    final FanoutEventHandler handler =
        new FanoutEventHandler() {
          @Override
          public String type() {
            return TYPE;
          }

          @Override
          public void handle(final String physicalTenantId, final JsonNode data) {
            if (failuresLeft.getAndDecrement() > 0) {
              throw new IllegalStateException("boom");
            }
            handled.add(data);
          }
        };
    puller =
        new HubFanoutPuller(
            "default", properties, Map.of(TYPE, handler), HttpClient.newHttpClient());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldAdvanceCursorAndReportHasMore() throws Exception {
    pagesByAfter.put("0", new Page("[" + inline(1, TYPE) + "," + inline(2, TYPE) + "]", true));
    pagesByAfter.put("2", new Page("[" + inline(3, TYPE) + "]", false));

    assertThat(puller.pollOnce()).isTrue();
    assertThat(puller.cursor()).isEqualTo(2);
    assertThat(puller.pollOnce()).isFalse();
    assertThat(puller.cursor()).isEqualTo(3);
    assertThat(requestedAfter).containsExactly("0", "2");
    assertThat(handled).extracting(n -> n.get("n").asInt()).containsExactly(1, 2, 3);
  }

  @Test
  void shouldResolveAndVerifyClaimCheck() throws Exception {
    final byte[] payload = "{\"n\":7}".getBytes(StandardCharsets.UTF_8);
    payloads.put("/hub/api/fanout/payloads/1", payload);
    pagesByAfter.put(
        "0",
        new Page("[" + ref(1, payload.length, PayloadResolver.sha256Hex(payload)) + "]", false));

    puller.pollOnce();

    assertThat(puller.cursor()).isEqualTo(1);
    assertThat(handled).extracting(n -> n.get("n").asInt()).containsExactly(7);
  }

  @Test
  void shouldNotAdvanceOnDigestMismatchUntilMaxAttempts() throws Exception {
    final byte[] payload = "{\"n\":7}".getBytes(StandardCharsets.UTF_8);
    payloads.put("/hub/api/fanout/payloads/1", payload);
    pagesByAfter.put("0", new Page("[" + ref(1, payload.length, "00".repeat(32)) + "]", false));

    assertThatThrownBy(puller::pollOnce).hasMessageContaining("sha256 mismatch");
    assertThat(puller.cursor()).isZero();
    assertThatThrownBy(puller::pollOnce);
    assertThat(puller.cursor()).isZero();

    puller.pollOnce(); // 3rd attempt: skipped as poison event

    assertThat(puller.cursor()).isEqualTo(1);
    assertThat(handled).isEmpty();
  }

  @Test
  void shouldRetryHandlerFailureWithoutAdvancing() throws Exception {
    failuresLeft.set(1);
    pagesByAfter.put("0", new Page("[" + inline(1, TYPE) + "]", false));

    assertThatThrownBy(puller::pollOnce);
    assertThat(puller.cursor()).isZero();

    puller.pollOnce();
    assertThat(puller.cursor()).isEqualTo(1);
    assertThat(handled).hasSize(1);
  }

  @Test
  void shouldStopAtGapInFeed() throws Exception {
    pagesByAfter.put("0", new Page("[" + inline(1, TYPE) + "," + inline(3, TYPE) + "]", false));

    assertThatThrownBy(puller::pollOnce).hasMessageContaining("expected seq 2, got 3");

    assertThat(puller.cursor()).isEqualTo(1);
    assertThat(handled).extracting(n -> n.get("n").asInt()).containsExactly(1);
  }

  @Test
  void shouldSkipUnknownType() throws Exception {
    pagesByAfter.put("0", new Page("[" + inline(1, "other.v1") + "]", false));

    puller.pollOnce();

    assertThat(puller.cursor()).isEqualTo(1);
    assertThat(handled).isEmpty();
  }

  private static String inline(final long seq, final String type) {
    return """
        {"specversion":"1.0","id":"%d","source":"/hub","type":"%s","seq":%d,"data":{"n":%d}}"""
        .formatted(seq, type, seq, seq);
  }

  private static String ref(final long seq, final long size, final String sha) {
    return """
        {"specversion":"1.0","id":"%d","source":"/hub","type":"%s","seq":%d,\
        "dataref":"api/fanout/payloads/%d?target=physical-tenant:oc-1:default","datasize":%d,"datasha256":"%s"}"""
        .formatted(seq, TYPE, seq, seq, size, sha);
  }

  private static String query(final URI uri, final String name) {
    for (final String pair : uri.getQuery().split("&")) {
      final var kv = pair.split("=", 2);
      if (kv[0].equals(name)) {
        return kv[1];
      }
    }
    return null;
  }

  private record Page(String body, boolean hasMore) {}
}
