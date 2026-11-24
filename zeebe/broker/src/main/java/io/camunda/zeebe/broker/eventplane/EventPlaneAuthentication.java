/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import java.io.IOException;
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
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How a broker authenticates to its Event Plane, as {@code camunda.event-plane} says: with OAuth
 * client credentials if a client id is set, with basic authentication if a username is set, and not
 * at all otherwise. Gives the value of the {@code Authorization} header for each request.
 *
 * <p>Whatever calls the Event Plane can share one: it is thread-safe, keeps one token for all its
 * callers until shortly before it expires, and fetches a new one once, however many callers need it
 * at the same time.
 */
public final class EventPlaneAuthentication {

  private static final Logger LOG = LoggerFactory.getLogger(EventPlaneAuthentication.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(30);
  private static final Duration DEFAULT_EXPIRY = Duration.ofSeconds(60);
  private static final int MAX_LOGGED_BODY = 500;

  private final EventPlaneCfg cfg;
  private final HttpClient http;
  private final InstantSource clock;

  private @Nullable String token;
  private Instant tokenExpiresAt = Instant.EPOCH;
  private @Nullable CompletableFuture<String> fetching;

  public EventPlaneAuthentication(
      final EventPlaneCfg cfg, final HttpClient http, final InstantSource clock) {
    this.cfg = cfg;
    this.http = http;
    this.clock = clock;
  }

  /** The value of the {@code Authorization} header, if the Event Plane needs one. */
  public CompletableFuture<Optional<String>> authorization() {
    if (cfg.getClientId() != null) {
      return token().thenApply(value -> Optional.of("Bearer " + value));
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

  /**
   * The Event Plane refused this authorization, as with an expired or revoked token: the next
   * request gets a new token, unless another caller got one meanwhile.
   */
  public synchronized void refused(final Optional<String> authorization) {
    if (token != null && authorization.equals(Optional.of("Bearer " + token))) {
      token = null;
    }
  }

  private synchronized CompletableFuture<String> token() {
    if (token != null && clock.instant().isBefore(tokenExpiresAt)) {
      return CompletableFuture.completedFuture(token);
    }
    if (fetching != null) {
      return fetching;
    }
    final var fetch = fetch();
    fetching = fetch;
    // Runs right away if the fetch has already failed, so this returns the local future.
    fetch.whenComplete((ignored, error) -> fetched(fetch));
    return fetch;
  }

  private synchronized void fetched(final CompletableFuture<String> fetch) {
    if (fetching == fetch) {
      fetching = null;
    }
  }

  private CompletableFuture<String> fetch() {
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
    // Each only if set, as the Java client's OAuthCredentialsProvider sends them.
    if (cfg.getAudience() != null) {
      form.append("&audience=").append(formValue(cfg.getAudience()));
    }
    if (cfg.getScope() != null) {
      form.append("&scope=").append(formValue(cfg.getScope()));
    }
    if (cfg.getResource() != null) {
      form.append("&resource=").append(formValue(cfg.getResource()));
    }
    final var request =
        HttpRequest.newBuilder()
            .uri(tokenUrl)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form.toString()))
            .build();
    final var requestedAt = clock.instant();
    LOG.debug("Fetching a token for the Event Plane from {}", tokenUrl);
    return http.sendAsync(request, BodyHandlers.ofString())
        .thenApply(response -> keep(response, requestedAt));
  }

  private synchronized String keep(final HttpResponse<String> response, final Instant requestedAt) {
    final var body = parse(response);
    final var accessToken = body.path("access_token");
    if (!accessToken.isTextual()) {
      throw new IllegalStateException("The token response has no access_token");
    }
    final var expiresIn =
        body.path("expires_in").canConvertToLong()
            ? Duration.ofSeconds(body.path("expires_in").asLong())
            : DEFAULT_EXPIRY;
    token = accessToken.asText();
    tokenExpiresAt = requestedAt.plus(expiresIn).minus(EXPIRY_BUFFER);
    return token;
  }

  private static JsonNode parse(final HttpResponse<String> response) {
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "The token request failed with HTTP "
              + response.statusCode()
              + ": "
              + truncated(response.body()));
    }
    try {
      return JSON.readTree(response.body());
    } catch (final IOException e) {
      throw new IllegalStateException("The token response is not JSON", e);
    }
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
