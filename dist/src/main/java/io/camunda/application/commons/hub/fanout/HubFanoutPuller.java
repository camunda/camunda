/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Long-polls Hub's fan-out feed for one physical tenant on a dedicated thread and dispatches each
 * event by type. Delivery is at-least-once: the in-memory cursor only advances after an event was
 * handled, and starts at 0 again whenever this puller is (re)created. Hub numbers the feed of each
 * target gap-free, so the puller stops at a gap instead of skipping events.
 */
final class HubFanoutPuller {

  private static final Logger LOGGER = LoggerFactory.getLogger(HubFanoutPuller.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<CloudEventEnvelope>> BATCH = new TypeReference<>() {};

  private final String physicalTenantId;
  private final String targetId;
  private final HubFanoutProperties properties;
  private final Map<String, FanoutEventHandler> handlers;
  private final HttpClient http;
  private final PayloadResolver payloadResolver;

  private volatile boolean running;
  private Thread thread;
  private long cursor;
  private long failingSeq = -1;
  private int failedAttempts;

  HubFanoutPuller(
      final String physicalTenantId,
      final HubFanoutProperties properties,
      final Map<String, FanoutEventHandler> handlers,
      final HttpClient http) {
    this.physicalTenantId = physicalTenantId;
    targetId = properties.targetId(physicalTenantId);
    this.properties = properties;
    this.handlers = handlers;
    this.http = http;
    payloadResolver = new PayloadResolver(http, properties.baseUrl());
  }

  synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    thread =
        Thread.ofPlatform()
            .daemon(true)
            .name("hub-fanout-puller-" + physicalTenantId)
            .start(this::run);
    LOGGER.info(
        "[pt={}] Started pulling Hub fan-out feed as target '{}'", physicalTenantId, targetId);
  }

  synchronized void stop() {
    if (!running) {
      return;
    }
    running = false;
    thread.interrupt();
    LOGGER.info("[pt={}] Stopped pulling Hub fan-out feed at cursor {}", physicalTenantId, cursor);
  }

  long cursor() {
    return cursor;
  }

  private void run() {
    while (running) {
      try {
        pollOnce();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (final Exception e) {
        LOGGER.warn("[pt={}] Pulling Hub fan-out feed failed: {}", physicalTenantId, e.toString());
        try {
          Thread.sleep(properties.backoff());
        } catch (final InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  /** One long-poll round trip. Returns whether Hub reported more events to pull right away. */
  boolean pollOnce() throws IOException, InterruptedException {
    final var uri =
        properties
            .baseUrl()
            .resolve(
                URI.create(
                    "api/fanout/events?target="
                        + URLEncoder.encode(targetId, StandardCharsets.UTF_8)
                        + "&after="
                        + cursor
                        + "&waitSeconds="
                        + properties.waitSeconds()));
    final var response =
        http.send(
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.waitSeconds() + 10L))
                .build(),
            BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IOException("Hub fan-out feed returned " + response.statusCode());
    }
    final List<CloudEventEnvelope> events = MAPPER.readValue(response.body(), BATCH);
    final boolean hasMore =
        response
            .headers()
            .firstValue(CloudEventEnvelope.HEADER_HAS_MORE)
            .map(Boolean::parseBoolean)
            .orElse(false);
    if (!events.isEmpty()) {
      LOGGER.info(
          "[pt={}] Pulled {} event(s) after cursor {} (hasMore={})",
          physicalTenantId,
          events.size(),
          cursor,
          hasMore);
    }
    for (final CloudEventEnvelope event : events) {
      // Hub numbers each target's feed gap-free; anything else means lost or reordered events.
      if (event.seq() != cursor + 1) {
        throw new IOException(
            "Gap in Hub fan-out feed: expected seq " + (cursor + 1) + ", got " + event.seq());
      }
      process(event);
    }
    return hasMore;
  }

  private void process(final CloudEventEnvelope event) throws IOException, InterruptedException {
    try {
      if (!CloudEventEnvelope.SPEC_VERSION.equals(event.specversion())) {
        throw new IllegalArgumentException("Unsupported specversion " + event.specversion());
      }
      final var handler = handlers.get(event.type());
      if (handler == null) {
        LOGGER.info(
            "[pt={}] Skipping event {} of unknown type {}",
            physicalTenantId,
            event.seq(),
            event.type());
      } else {
        handler.handle(physicalTenantId, payloadResolver.resolve(event));
      }
      advance(event.seq());
    } catch (final InterruptedException e) {
      throw e;
    } catch (final Exception e) {
      failedAttempts = failingSeq == event.seq() ? failedAttempts + 1 : 1;
      failingSeq = event.seq();
      if (failedAttempts >= properties.maxAttempts()) {
        // PoC stand-in for a dead-letter queue: a poison event must not block the whole feed.
        LOGGER.error(
            "[pt={}] Skipping event {} after {} failed attempts",
            physicalTenantId,
            event.seq(),
            failedAttempts,
            e);
        advance(event.seq());
        return;
      }
      throw e instanceof IOException io ? io : new IOException(e);
    }
  }

  private void advance(final long seq) {
    cursor = seq;
    failingSeq = -1;
    failedAttempts = 0;
  }
}
