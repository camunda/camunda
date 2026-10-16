/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import io.camunda.zeebe.util.RetryDelayStrategy;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Registers a physical tenant in its Event Plane, with {@code PUT
 * /v1/environments/{clusterId}/{physicalTenantId}} and no registration status. The Event Plane
 * creates and registers an environment it doesn't know, and leaves one it knows as it is, so this
 * never undoes a deregistration.
 *
 * <p>Best effort: it never holds up the partition, and retries with backoff until the Event Plane
 * answers. The requests run on the HTTP client's threads, and their outcome comes back to the
 * actor.
 */
public final class EventPlaneRegistration extends Actor {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofSeconds(30);
  private static final int MAX_LOGGED_BODY = 500;

  private final EventPlaneCfg cfg;
  private final URI endpoint;
  private final String physicalTenantId;
  private final Supplier<Optional<String>> clusterId;
  private final RetryDelayStrategy retryDelay;
  private final HttpClient http;

  private @Nullable String token;
  private Instant tokenExpiresAt = Instant.EPOCH;

  public EventPlaneRegistration(
      final EventPlaneCfg cfg,
      final PartitionId partitionId,
      final Supplier<Optional<String>> clusterId) {
    this(
        cfg,
        partitionId,
        clusterId,
        new ExponentialBackoffRetryDelay(Duration.ofMinutes(5), Duration.ofSeconds(1)));
  }

  @VisibleForTesting
  EventPlaneRegistration(
      final EventPlaneCfg cfg,
      final PartitionId partitionId,
      final Supplier<Optional<String>> clusterId,
      final RetryDelayStrategy retryDelay) {
    super("EventPlaneRegistration", partitionId);
    this.cfg = cfg;
    endpoint = requireEndpoint(cfg);
    physicalTenantId = partitionId.group();
    this.clusterId = clusterId;
    this.retryDelay = retryDelay;
    http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
  }

  @Override
  protected void onActorStarted() {
    register();
  }

  @Override
  protected void onActorClosing() {
    http.shutdownNow();
  }

  private void register() {
    final var id = clusterId.get();
    if (id.isEmpty()) {
      retry("the cluster id isn't known yet");
      return;
    }
    authorization()
        .thenCompose(
            authorization ->
                http.sendAsync(request(id.get(), authorization), BodyHandlers.ofString()))
        .whenComplete((response, error) -> actor.run(() -> onAnswer(id.get(), response, error)));
  }

  private void onAnswer(
      final String clusterId,
      final @Nullable HttpResponse<String> response,
      final @Nullable Throwable error) {
    if (error != null || response == null) {
      retry("the request failed: " + error);
      return;
    }
    switch (response.statusCode()) {
      case 201 ->
          LOG.info(
              "Registered physical tenant {} of cluster {} in the Event Plane at {}",
              physicalTenantId,
              clusterId,
              endpoint);
      case 200 ->
          LOG.info(
              "The Event Plane at {} knows physical tenant {} of cluster {} already",
              endpoint,
              physicalTenantId,
              clusterId);
      default -> {
        if (response.statusCode() == 401) {
          // An expired or revoked token: the next attempt gets a new one.
          token = null;
        }
        retry("HTTP " + response.statusCode() + ": " + truncated(response.body()));
      }
    }
  }

  private void retry(final String reason) {
    final var delay = retryDelay.nextDelay();
    LOG.warn(
        "Failed to register physical tenant {} in the Event Plane at {}, retrying in {}: {}",
        physicalTenantId,
        endpoint,
        delay,
        reason);
    actor.schedule(delay, this::register);
  }

  private HttpRequest request(final String clusterId, final Optional<String> authorization) {
    final var request =
        HttpRequest.newBuilder()
            .uri(environmentUri(clusterId))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .PUT(BodyPublishers.ofString("{}"));
    authorization.ifPresent(value -> request.header("Authorization", value));
    return request.build();
  }

  private URI environmentUri(final String clusterId) {
    final var base = endpoint.toString().replaceAll("/+$", "");
    return URI.create(
        base + "/v1/environments/" + pathSegment(clusterId) + "/" + pathSegment(physicalTenantId));
  }

  /** OAuth if a client id is set, basic if a username is, otherwise none. */
  private CompletableFuture<Optional<String>> authorization() {
    if (cfg.getClientId() != null) {
      return bearerToken().thenApply(value -> Optional.of("Bearer " + value));
    }
    if (cfg.getUsername() != null) {
      final var credentials = cfg.getUsername() + ":" + nullToEmpty(cfg.getPassword());
      return CompletableFuture.completedFuture(
          Optional.of(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString(credentials.getBytes(StandardCharsets.UTF_8))));
    }
    return CompletableFuture.completedFuture(Optional.empty());
  }

  /** Runs on the actor, which alone reads and writes the cached token. */
  private CompletableFuture<String> bearerToken() {
    final var cached = token;
    if (cached != null && Instant.now().isBefore(tokenExpiresAt)) {
      return CompletableFuture.completedFuture(cached);
    }
    final var tokenUrl = cfg.getTokenUrl();
    if (tokenUrl == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("camunda.event-plane.token-url is not set"));
    }
    final var form =
        new StringBuilder("grant_type=client_credentials")
            .append("&client_id=")
            .append(formValue(cfg.getClientId()))
            .append("&client_secret=")
            .append(formValue(cfg.getClientSecret()));
    if (cfg.getAudience() != null) {
      form.append("&audience=").append(formValue(cfg.getAudience()));
    }
    final var request =
        HttpRequest.newBuilder()
            .uri(tokenUrl)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form.toString()))
            .build();
    final var requestedAt = Instant.now();
    return http.sendAsync(request, BodyHandlers.ofString())
        .thenApply(
            response -> {
              final var accessToken = accessToken(response);
              final var expiresIn = expiresIn(response);
              // The HTTP client's thread hands the token back; the actor caches it.
              actor.run(
                  () -> {
                    token = accessToken;
                    tokenExpiresAt = requestedAt.plus(expiresIn).minus(TOKEN_EXPIRY_BUFFER);
                  });
              return accessToken;
            });
  }

  private static String accessToken(final HttpResponse<String> response) {
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "the token request failed with HTTP "
              + response.statusCode()
              + ": "
              + truncated(response.body()));
    }
    try {
      final var accessToken = JSON.readTree(response.body()).path("access_token");
      if (!accessToken.isTextual()) {
        throw new IllegalStateException("the token response has no access_token");
      }
      return accessToken.asText();
    } catch (final IOException e) {
      throw new IllegalStateException("the token response is not JSON", e);
    }
  }

  private static Duration expiresIn(final HttpResponse<String> response) {
    try {
      return Duration.ofSeconds(JSON.readTree(response.body()).path("expires_in").asLong(60));
    } catch (final IOException e) {
      return Duration.ofSeconds(60);
    }
  }

  private static URI requireEndpoint(final EventPlaneCfg cfg) {
    final var endpoint = cfg.getEndpoint();
    if (endpoint == null) {
      throw new IllegalArgumentException("camunda.event-plane.endpoint is not set");
    }
    return endpoint;
  }

  private static String pathSegment(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String formValue(final @Nullable String value) {
    return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8);
  }

  private static String nullToEmpty(final @Nullable String value) {
    return value == null ? "" : value;
  }

  private static String truncated(final String body) {
    return body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "…";
  }
}
