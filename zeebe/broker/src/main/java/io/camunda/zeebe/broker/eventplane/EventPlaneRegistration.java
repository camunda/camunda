/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
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
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Registers a physical tenant in its Event Plane, with {@code PUT
 * /v1/environments/{clusterId}/{physicalTenantId}} and no registration status. The Event Plane
 * creates and registers an environment it doesn't know, and leaves one it knows as it is, so this
 * never undoes a deregistration.
 *
 * <p>A broker has one per tenant, which the leaders of the tenant's partitions on the broker share:
 * each says when it {@link #lead leads} and when it {@link #stopLeading stops}, and all wait for
 * the same {@link #registered registration}. It registers once while any of them leads, and retries
 * with backoff until the Event Plane answers, but only while one of them leads. Best effort: it
 * never holds up a partition. The requests run on the HTTP client's threads, and their outcome
 * comes back to the actor.
 *
 * <p>It logs little: a failure at debug level until attempts have failed for a minute, then a
 * warning, again every hour while they keep failing, and a line at info level once they succeed
 * after a warning. Otherwise only a newly registered environment is logged at info level.
 */
public final class EventPlaneRegistration extends Actor {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofSeconds(30);
  private static final int MAX_LOGGED_BODY = 500;
  private static final Duration WARN_AFTER = Duration.ofMinutes(1);
  private static final Duration WARN_EVERY = Duration.ofHours(1);

  private final EventPlaneCfg cfg;
  private final URI endpoint;
  private final String physicalTenantId;
  private final Supplier<Optional<String>> clusterId;
  private final RetryDelayStrategy retryDelay;
  private final HttpClient http;
  private final InstantSource clock;
  private final FailureEscalation failures = new FailureEscalation(WARN_AFTER, WARN_EVERY);
  private final CompletableActorFuture<Void> registered = new CompletableActorFuture<>();
  private final Set<Integer> leaders = new HashSet<>();

  private boolean attempting;
  private @Nullable ScheduledTimer nextAttempt;
  private @Nullable String token;
  private Instant tokenExpiresAt = Instant.EPOCH;

  public EventPlaneRegistration(
      final EventPlaneCfg cfg,
      final String physicalTenantId,
      final Supplier<Optional<String>> clusterId) {
    this(
        cfg,
        physicalTenantId,
        clusterId,
        new ExponentialBackoffRetryDelay(Duration.ofMinutes(5), Duration.ofSeconds(1)),
        InstantSource.system());
  }

  @VisibleForTesting
  EventPlaneRegistration(
      final EventPlaneCfg cfg,
      final String physicalTenantId,
      final Supplier<Optional<String>> clusterId,
      final RetryDelayStrategy retryDelay,
      final InstantSource clock) {
    super(
        "EventPlaneRegistration-" + physicalTenantId,
        null,
        Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.cfg = cfg;
    endpoint = requireEndpoint(cfg);
    this.physicalTenantId = physicalTenantId;
    this.clusterId = clusterId;
    this.retryDelay = retryDelay;
    this.clock = clock;
    http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
  }

  /**
   * This broker leads the partition now: registers the tenant unless it is registered already, and
   * returns the registration, which every leader waits for.
   */
  public ActorFuture<Void> lead(final int partition) {
    actor.run(
        () -> {
          leaders.add(partition);
          if (!registered.isDone() && !attempting && nextAttempt == null) {
            register();
          }
        });
    return registered;
  }

  /** This broker leads the partition no more. Without a leader, registering pauses. */
  public void stopLeading(final int partition) {
    actor.run(
        () -> {
          leaders.remove(partition);
          if (leaders.isEmpty() && nextAttempt != null) {
            pause();
          }
        });
  }

  /** Completes once the tenant is registered; fails if the broker stops before. */
  public ActorFuture<Void> registered() {
    return registered;
  }

  @Override
  protected void onActorClosing() {
    http.shutdownNow();
    if (!registered.isDone()) {
      registered.completeExceptionally(
          new IllegalStateException("The broker stopped before the tenant was registered"));
    }
  }

  private void register() {
    attempting = true;
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
    attempting = false;
    if (error != null || response == null) {
      retry(error == null ? "no answer" : describe(error));
      return;
    }
    switch (response.statusCode()) {
      case 200, 201 -> registered(clusterId, response.statusCode() == 201);
      default -> {
        if (response.statusCode() == 401) {
          // An expired or revoked token: the next attempt gets a new one.
          token = null;
        }
        retry("HTTP " + response.statusCode() + ": " + truncated(response.body()));
      }
    }
  }

  private void registered(final String clusterId, final boolean created) {
    registered.complete(null);
    final var failedFor = failures.failingFor(clock.instant()).truncatedTo(ChronoUnit.SECONDS);
    if (failures.succeeded()) {
      LOG.info(
          "Registered physical tenant {} of cluster {} in the Event Plane at {}, after failing for {}",
          physicalTenantId,
          clusterId,
          endpoint,
          failedFor);
    } else if (created) {
      LOG.info(
          "Registered physical tenant {} of cluster {} in the Event Plane at {}",
          physicalTenantId,
          clusterId,
          endpoint);
    } else {
      // Every partition's leader registers the tenant, so this is the usual answer.
      LOG.debug(
          "The Event Plane at {} knows physical tenant {} of cluster {} already",
          endpoint,
          physicalTenantId,
          clusterId);
    }
  }

  private void retry(final String reason) {
    attempting = false;
    if (leaders.isEmpty()) {
      pause();
      return;
    }
    final var now = clock.instant();
    final var delay = retryDelay.nextDelay();
    if (failures.failed(now)) {
      LOG.warn(
          "Failed to register physical tenant {} in the Event Plane at {} for {}, retrying in {}: {}",
          physicalTenantId,
          endpoint,
          failures.failingFor(now).truncatedTo(ChronoUnit.SECONDS),
          delay,
          reason);
    } else {
      LOG.debug(
          "Failed to register physical tenant {} in the Event Plane at {}, retrying in {}: {}",
          physicalTenantId,
          endpoint,
          delay,
          reason);
    }
    nextAttempt =
        actor.schedule(
            delay,
            () -> {
              nextAttempt = null;
              register();
            });
  }

  /** Stops retrying until a partition's leader asks again, and forgets the failures so far. */
  private void pause() {
    if (nextAttempt != null) {
      nextAttempt.cancel();
      nextAttempt = null;
    }
    retryDelay.reset();
    failures.reset();
    LOG.debug(
        "Paused registering physical tenant {} in the Event Plane at {}: this broker leads none of"
            + " its partitions",
        physicalTenantId,
        endpoint);
  }

  /**
   * The failure and its causes, without the wrappers of the asynchronous calls: {@code
   * ConnectException, caused by ConnectException: Connection refused} rather than {@code
   * java.util.concurrent.CompletionException: java.net.ConnectException}.
   */
  @VisibleForTesting
  static String describe(final Throwable error) {
    final var parts = new ArrayList<String>();
    Throwable cause = error;
    for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
      if (cause instanceof CompletionException || cause instanceof ExecutionException) {
        continue;
      }
      final var name = cause.getClass().getSimpleName();
      final var part = cause.getMessage() == null ? name : name + ": " + cause.getMessage();
      if (parts.isEmpty() || !parts.getLast().equals(part)) {
        parts.add(part);
      }
    }
    return parts.isEmpty() ? error.toString() : String.join(", caused by ", parts);
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
