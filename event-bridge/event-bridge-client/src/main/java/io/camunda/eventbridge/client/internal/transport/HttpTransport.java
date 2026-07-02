/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.transport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.eventbridge.client.EventBridgeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * The single HTTP transport for the Event Bridge client: the only class in the module that
 * references the JDK HTTP client. It owns the underlying {@link HttpClient} and the {@link
 * ObjectMapper}, builds requests against the gateway base URL, and exposes typed JSON and binary
 * operations so every caller (client facade, topic admin, consumer collaborators) routes through
 * one place.
 *
 * <p>All async operations run on the shared HTTP client's executor. The one blocking send ({@link
 * #sendJsonSync}) exists for callers that are already running on an executor thread and want a
 * straight-line request/response.
 */
public final class HttpTransport implements AutoCloseable {

  /** Base request timeout applied to every non-fetch request. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  /** Connect timeout for the underlying HTTP client. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final String gatewayUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * Creates a transport rooted at {@code gatewayUrl} with a default {@link HttpClient} and {@link
   * ObjectMapper} (lenient on unknown JSON fields the gateway may add).
   *
   * @param gatewayUrl base URL of the Event Bridge gateway, without a trailing slash
   */
  public HttpTransport(final String gatewayUrl) {
    this(
        gatewayUrl,
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
  }

  /** Visible-for-testing constructor allowing an explicit client and mapper to be injected. */
  public HttpTransport(
      final String gatewayUrl, final HttpClient httpClient, final ObjectMapper objectMapper) {
    this.gatewayUrl = gatewayUrl;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  /** Returns the gateway base URL this transport is rooted at (no trailing slash). */
  public String gatewayUrl() {
    return gatewayUrl;
  }

  // -------------------------------------------------------------------------
  // JSON — async

  /** Sends {@code GET path}, expecting HTTP 200, and parses the body as {@code type}. */
  public <T> CompletableFuture<T> getJson(final String path, final Class<T> type, final String op) {
    final var request = jsonRequest(path).GET().build();
    return sendAsyncString(request)
        .thenApply(
            response -> {
              expectStatus(response, 200, op);
              return readBody(response.body(), type, op);
            });
  }

  /** Sends {@code POST path} with a JSON {@code body}, expecting HTTP 200, and parses the body. */
  public <T> CompletableFuture<T> postJson(
      final String path, final Object body, final Class<T> type, final String op) {
    final var request =
        jsonRequest(path).POST(HttpRequest.BodyPublishers.ofString(writeBody(body, op))).build();
    return sendAsyncString(request)
        .thenApply(
            response -> {
              expectStatus(response, 200, op);
              return readBody(response.body(), type, op);
            });
  }

  /**
   * Sends {@code POST path} with a JSON {@code body}, expecting {@code expectedStatus}, and
   * discarding the response body.
   */
  public CompletableFuture<Void> postJson(
      final String path, final Object body, final int expectedStatus, final String op) {
    final var request =
        jsonRequest(path).POST(HttpRequest.BodyPublishers.ofString(writeBody(body, op))).build();
    return sendAsyncString(request)
        .thenApply(
            response -> {
              expectStatus(response, expectedStatus, op);
              return null;
            });
  }

  /** Sends {@code DELETE path}, expecting {@code expectedStatus}, discarding the response body. */
  public CompletableFuture<Void> deleteJson(
      final String path, final int expectedStatus, final String op) {
    final var request = jsonRequest(path).DELETE().build();
    return sendAsyncString(request)
        .thenApply(
            response -> {
              expectStatus(response, expectedStatus, op);
              return null;
            });
  }

  // -------------------------------------------------------------------------
  // JSON — sync

  /**
   * Sends {@code POST path} with a JSON {@code body} asynchronously and returns the raw response
   * status and body without asserting a status. Callers that apply per-status handling (join) use
   * this.
   */
  public CompletableFuture<SyncResponse> postJsonRaw(
      final String path, final Object body, final String op) {
    final var request =
        jsonRequest(path).POST(HttpRequest.BodyPublishers.ofString(writeBody(body, op))).build();
    return sendAsyncString(request)
        .thenApply(response -> new SyncResponse(response.statusCode(), response.body()));
  }

  /**
   * Sends {@code POST path} with a JSON {@code body} and returns the raw response, blocking the
   * calling thread. Intended for callers already running on an executor thread (the heartbeat loop,
   * rejoin, and commit paths) that want a straight-line request/response with per-status handling.
   *
   * @return the raw string-bodied HTTP response
   */
  public SyncResponse sendJsonSync(final String path, final Object body, final String op) {
    final var request =
        jsonRequest(path).POST(HttpRequest.BodyPublishers.ofString(writeBody(body, op))).build();
    final HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EventBridgeException(op + " HTTP request failed: " + e.getMessage(), e);
    }
    return new SyncResponse(response.statusCode(), response.body());
  }

  /** A synchronous HTTP response reduced to its status code and string body. */
  public record SyncResponse(int statusCode, String body) {}

  // -------------------------------------------------------------------------
  // Binary

  /**
   * Sends {@code GET path} accepting {@code application/octet-stream} and returns the raw response
   * status and body, using a fetch-tuned timeout of the base timeout plus {@code maxWaitMs} so the
   * HTTP client never aborts a legitimately parked long-poll before the broker returns.
   */
  public CompletableFuture<BinaryResponse> getBinary(final String path, final long maxWaitMs) {
    final var request =
        HttpRequest.newBuilder()
            .uri(uri(path))
            .header("Accept", "application/octet-stream")
            .timeout(DEFAULT_TIMEOUT.plusMillis(maxWaitMs))
            .GET()
            .build();
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> new BinaryResponse(response.statusCode(), response.body()));
  }

  /**
   * Sends {@code POST path} with an {@code application/octet-stream} body and returns the raw
   * response status and string body (publish-response parsing stays in the caller).
   */
  public CompletableFuture<SyncResponse> postOctetStream(final String path, final byte[] body) {
    final var request =
        HttpRequest.newBuilder()
            .uri(uri(path))
            .timeout(DEFAULT_TIMEOUT)
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    return sendAsyncString(request)
        .thenApply(response -> new SyncResponse(response.statusCode(), response.body()));
  }

  /** A binary HTTP response reduced to its status code and body bytes. */
  public record BinaryResponse(int statusCode, byte[] body) {}

  // -------------------------------------------------------------------------
  // JSON (de)serialization — shared with callers that parse bodies themselves

  /**
   * Parses {@code body} as {@code type}, wrapping any failure in an {@link EventBridgeException}.
   */
  public <T> T readBody(final String body, final Class<T> type, final String op) {
    try {
      return objectMapper.readValue(body, type);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to parse " + op + " response", e);
    }
  }

  /** Serializes {@code value} to JSON, wrapping any failure in an {@link EventBridgeException}. */
  public String writeBody(final Object value, final String op) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final IOException e) {
      throw new EventBridgeException("Failed to serialize " + op + " request", e);
    }
  }

  @Override
  public void close() {
    httpClient.close();
  }

  // -------------------------------------------------------------------------

  private CompletableFuture<HttpResponse<String>> sendAsyncString(final HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder jsonRequest(final String path) {
    return HttpRequest.newBuilder()
        .uri(uri(path))
        .timeout(DEFAULT_TIMEOUT)
        .header("Content-Type", "application/json");
  }

  private URI uri(final String path) {
    return URI.create(gatewayUrl + path);
  }

  private void expectStatus(
      final HttpResponse<String> response, final int expected, final String op) {
    if (response.statusCode() != expected) {
      throw new EventBridgeException(
          op + " failed: HTTP " + response.statusCode() + " — " + response.body());
    }
  }
}
