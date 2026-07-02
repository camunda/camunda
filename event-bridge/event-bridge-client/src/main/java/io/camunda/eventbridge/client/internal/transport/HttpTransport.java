/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.transport;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.camunda.eventbridge.client.EventBridgeException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * The single HTTP transport for the Event Bridge client: the only class in the module that
 * references the JDK HTTP client. It owns the underlying {@link HttpClient}, builds requests
 * against the gateway base URL, and exposes typed protobuf and raw-binary operations so every
 * caller (client facade, topic admin, consumer collaborators) routes through one place.
 *
 * <p>Coordination and admin messages travel as binary protobuf ({@code application/x-protobuf}):
 * requests are serialized with {@link Message#toByteArray()} and responses parsed with a generated
 * {@link Parser}. Publish and fetch stay on the raw batch codec ({@code application/octet-stream});
 * the publish response is negotiated to protobuf so it can be parsed without JSON.
 *
 * <p>All operations are non-blocking and run on the shared HTTP client's executor.
 */
public final class HttpTransport implements AutoCloseable {

  /** Media type for the binary protobuf representation. */
  public static final String PROTOBUF = "application/x-protobuf";

  /** Base request timeout applied to every non-fetch request. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  /** Connect timeout for the underlying HTTP client. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final String gatewayUrl;
  private final HttpClient httpClient;

  /**
   * Creates a transport rooted at {@code gatewayUrl} with a default {@link HttpClient}.
   *
   * @param gatewayUrl base URL of the Event Bridge gateway, without a trailing slash
   */
  public HttpTransport(final String gatewayUrl) {
    this(gatewayUrl, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  /** Visible-for-testing constructor allowing an explicit client to be injected. */
  public HttpTransport(final String gatewayUrl, final HttpClient httpClient) {
    this.gatewayUrl = gatewayUrl;
    this.httpClient = httpClient;
  }

  /** Returns the gateway base URL this transport is rooted at (no trailing slash). */
  public String gatewayUrl() {
    return gatewayUrl;
  }

  // -------------------------------------------------------------------------
  // Protobuf — typed (status asserted by this transport)

  /**
   * Sends {@code POST path} with a protobuf {@code body}, expecting HTTP 200, and parses the
   * response body with {@code parser}.
   */
  public <T extends Message> CompletableFuture<T> postProtobuf(
      final String path, final Message body, final Parser<T> parser, final String op) {
    return sendProtobuf("POST", path, body)
        .thenApply(
            response -> {
              expectStatus(response, 200, op);
              return parse(response.body(), parser, op);
            });
  }

  /**
   * Sends {@code GET path} accepting protobuf, expecting HTTP 200, and parses the response body
   * with {@code parser}.
   */
  public <T extends Message> CompletableFuture<T> getProtobuf(
      final String path, final Parser<T> parser, final String op) {
    final var request =
        protobufRequest(path).GET().header("Accept", PROTOBUF).timeout(DEFAULT_TIMEOUT).build();
    return sendAsyncBytes(request)
        .thenApply(
            response -> {
              expectStatus(response, 200, op);
              return parse(response.body(), parser, op);
            });
  }

  /**
   * Sends {@code POST path} with a protobuf {@code body}, expecting {@code expectedStatus}, and
   * discarding the response body.
   */
  public CompletableFuture<Void> postProtobuf(
      final String path, final Message body, final int expectedStatus, final String op) {
    return sendProtobuf("POST", path, body)
        .thenApply(
            response -> {
              expectStatus(response, expectedStatus, op);
              return null;
            });
  }

  /** Sends {@code DELETE path}, expecting {@code expectedStatus}, discarding the response body. */
  public CompletableFuture<Void> delete(
      final String path, final int expectedStatus, final String op) {
    final var request = protobufRequest(path).DELETE().timeout(DEFAULT_TIMEOUT).build();
    return sendAsyncBytes(request)
        .thenApply(
            response -> {
              expectStatus(response, expectedStatus, op);
              return null;
            });
  }

  // -------------------------------------------------------------------------
  // Protobuf — raw (per-status handling by the caller)

  /**
   * Sends {@code POST path} with a protobuf {@code body} asynchronously and returns the raw
   * response status and body without asserting a status. Callers that apply per-status handling
   * (join, heartbeat, rejoin, commit) use this.
   */
  public CompletableFuture<BinaryResponse> postProtobufRaw(
      final String path, final Message body, final String op) {
    return sendProtobuf("POST", path, body)
        .thenApply(response -> new BinaryResponse(response.statusCode(), response.body()));
  }

  /**
   * Sends {@code DELETE path} asynchronously and returns the raw response status and body without
   * asserting a status. Callers that apply per-status handling (leave) use this.
   */
  public CompletableFuture<BinaryResponse> deleteRaw(final String path, final String op) {
    final var request = protobufRequest(path).DELETE().timeout(DEFAULT_TIMEOUT).build();
    return sendAsyncBytes(request)
        .thenApply(response -> new BinaryResponse(response.statusCode(), response.body()));
  }

  // -------------------------------------------------------------------------
  // Binary — raw batch (publish / fetch)

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
    return sendAsyncBytes(request)
        .thenApply(response -> new BinaryResponse(response.statusCode(), response.body()));
  }

  /**
   * Sends {@code POST path} with an {@code application/octet-stream} body (a pre-encoded batch) and
   * accepts a protobuf response, returning the raw status and body bytes (publish-response parsing
   * stays in the caller).
   */
  public CompletableFuture<BinaryResponse> postOctetStream(final String path, final byte[] body) {
    final var request =
        HttpRequest.newBuilder()
            .uri(uri(path))
            .timeout(DEFAULT_TIMEOUT)
            .header("Content-Type", "application/octet-stream")
            .header("Accept", PROTOBUF)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    return sendAsyncBytes(request)
        .thenApply(response -> new BinaryResponse(response.statusCode(), response.body()));
  }

  /** A binary HTTP response reduced to its status code and body bytes. */
  public record BinaryResponse(int statusCode, byte[] body) {}

  // -------------------------------------------------------------------------
  // protobuf (de)serialization — shared with callers that parse bodies themselves

  /**
   * Parses {@code body} with {@code parser}, wrapping any failure in an {@link
   * EventBridgeException}.
   */
  public <T extends Message> T parse(final byte[] body, final Parser<T> parser, final String op) {
    try {
      return parser.parseFrom(body == null ? new byte[0] : body);
    } catch (final InvalidProtocolBufferException e) {
      throw new EventBridgeException("Failed to parse " + op + " response", e);
    }
  }

  @Override
  public void close() {
    httpClient.close();
  }

  // -------------------------------------------------------------------------

  private CompletableFuture<HttpResponse<byte[]>> sendProtobuf(
      final String method, final String path, final Message body) {
    final var request =
        protobufRequest(path)
            .timeout(DEFAULT_TIMEOUT)
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
    return sendAsyncBytes(request);
  }

  private CompletableFuture<HttpResponse<byte[]>> sendAsyncBytes(final HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpRequest.Builder protobufRequest(final String path) {
    return HttpRequest.newBuilder()
        .uri(uri(path))
        .header("Content-Type", PROTOBUF)
        .header("Accept", PROTOBUF);
  }

  private URI uri(final String path) {
    return URI.create(gatewayUrl + path);
  }

  private void expectStatus(
      final HttpResponse<byte[]> response, final int expected, final String op) {
    if (response.statusCode() != expected) {
      throw new EventBridgeException(op + " failed: HTTP " + response.statusCode());
    }
  }
}
