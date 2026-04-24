/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.appint.transport.Authentication.OAuthCredentialsProvider;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A self-contained OAuth 2.0 client-credentials grant provider.
 *
 * <p>Fetches an access token from the configured authorization server and caches it in memory. A
 * single daemon background thread keeps the token valid ahead of time by proactively rotating it
 * once less than {@link #REFRESH_MARGIN} of its lifetime remains, so request threads almost always
 * hit a warm cache. A synchronous fetch is retained as a safety net (e.g. immediately after an
 * {@link #invalidate()} or before the first background fetch completes).
 *
 * <p>It is intentionally minimal and has no dependency on the camunda-client library.
 */
public final class DefaultOAuthCredentialsProvider implements OAuthCredentialsProvider {

  static final String AUTHORIZATION_HEADER = "Authorization";
  static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

  /** Token is safe to attach to an outgoing request while at least this margin remains. */
  static final Duration USABLE_SAFETY_MARGIN = Duration.ofSeconds(30);

  /** Proactively rotate the token once less than this much of its lifetime remains. */
  static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

  /** Backoff before retrying a failed background token fetch. */
  static final Duration MIN_REFRESH_RETRY_BACKOFF = Duration.ofSeconds(5);

  /** Assumed token lifetime when the response does not report a positive {@code expires_in}. */
  static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  private final String authorizationServerUrl;
  private final String clientId;
  private final String clientSecret;
  private final String audience;
  private final String scope;
  private final String resource;
  private final Duration connectTimeout;
  private final Duration readTimeout;

  private final Object tokenLock = new Object();
  private volatile CachedToken cachedToken;

  private final CloseableHttpClient httpClient;
  private final ScheduledExecutorService scheduler;
  private volatile boolean closed;

  public DefaultOAuthCredentialsProvider(
      final String authorizationServerUrl,
      final String clientId,
      final String clientSecret,
      final String audience,
      final String scope,
      final String resource,
      final Duration connectTimeout,
      final Duration readTimeout) {
    this.authorizationServerUrl =
        Objects.requireNonNull(authorizationServerUrl, "authorizationServerUrl");
    this.clientId = Objects.requireNonNull(clientId, "clientId");
    this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
    this.audience = audience;
    this.scope = scope;
    this.resource = resource;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;

    // Build the shared client once; it is thread-safe and reused by both the background refresher
    // thread and any synchronous fetch, avoiding per-request connection and TLS setup.
    httpClient = HttpClientBuilder.create().build();

    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final Thread t = new Thread(r, "app-integrations-oauth-token-refresher");
              t.setDaemon(true);
              return t;
            });
    // Run the initial fetch off-thread so construction never blocks or throws on a token-endpoint
    // failure.
    scheduler.schedule(this::refreshTokenTask, 0, TimeUnit.MILLISECONDS);
  }

  @Override
  public void applyCredentials(final BiConsumer<String, String> headerConsumer) throws IOException {
    final CachedToken token = obtainToken();
    headerConsumer.accept(AUTHORIZATION_HEADER, token.tokenType + " " + token.accessToken);
  }

  @Override
  public void invalidate() {
    synchronized (tokenLock) {
      cachedToken = null;
    }
    // Rotate immediately in the background as well, so the token is valid even with no in-flight
    // request. The synchronous applyCredentials path also covers the retry.
    if (!closed) {
      try {
        scheduler.execute(this::refreshTokenTask);
      } catch (final RejectedExecutionException ignored) {
        // closed concurrently; ignore
      }
    }
  }

  @Override
  public void close() {
    closed = true;
    scheduler.shutdownNow();
    try {
      scheduler.awaitTermination(5, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    try {
      httpClient.close();
    } catch (final IOException e) {
      log.debug("Failed to close OAuth token http client", e);
    }
  }

  private CachedToken obtainToken() throws IOException {
    final CachedToken current = cachedToken;
    if (current != null && current.isUsable()) {
      return current;
    }
    synchronized (tokenLock) {
      if (cachedToken != null && cachedToken.isUsable()) {
        return cachedToken;
      }
      cachedToken = fetchToken();
      return cachedToken;
    }
  }

  /** Runs on the refresher thread; fetches the token when needed and self-reschedules. */
  private void refreshTokenTask() {
    if (closed) {
      return;
    }
    Duration nextDelay;
    try {
      synchronized (tokenLock) {
        if (cachedToken == null || cachedToken.needsProactiveRefresh()) {
          cachedToken = fetchToken();
        }
        nextDelay = cachedToken.untilRefresh();
      }
      // Never schedule a tight loop if clock math underflows.
      if (nextDelay.isZero()) {
        nextDelay = MIN_REFRESH_RETRY_BACKOFF;
      }
    } catch (final Exception e) {
      log.warn("Failed to proactively refresh OAuth token; will retry.", e);
      nextDelay = MIN_REFRESH_RETRY_BACKOFF;
    }
    scheduleNext(nextDelay);
  }

  private void scheduleNext(final Duration delay) {
    if (closed) {
      return;
    }
    try {
      scheduler.schedule(this::refreshTokenTask, delay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final RejectedExecutionException ignored) {
      // scheduler shut down concurrently with close(); nothing to do
    }
  }

  private CachedToken fetchToken() throws IOException {
    final HttpPost post = new HttpPost(authorizationServerUrl);
    post.setHeader("Content-Type", CONTENT_TYPE_FORM);
    post.setHeader("Accept", "application/json");
    post.setEntity(new StringEntity(buildFormBody(), StandardCharsets.UTF_8));

    final RequestConfig.Builder requestConfig = RequestConfig.custom();
    if (connectTimeout != null) {
      requestConfig.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    }
    if (readTimeout != null) {
      requestConfig.setSocketTimeout(Math.toIntExact(readTimeout.toMillis()));
    }
    post.setConfig(requestConfig.build());

    try (final CloseableHttpResponse response = httpClient.execute(post)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      final String body =
          response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
      if (statusCode < 200 || statusCode >= 300) {
        throw new IOException(
            "Failed to fetch OAuth token: HTTP "
                + statusCode
                + " "
                + response.getStatusLine().getReasonPhrase()
                + " - "
                + body);
      }
      final TokenResponse parsed = JSON_MAPPER.readValue(body, TokenResponse.class);
      if (parsed.accessToken == null || parsed.accessToken.isBlank()) {
        throw new IOException("OAuth token response did not contain an access_token");
      }
      final String tokenType = parsed.tokenType != null ? parsed.tokenType : "Bearer";
      final Instant expiresAt =
          parsed.expiresInSeconds > 0
              ? Instant.now().plusSeconds(parsed.expiresInSeconds)
              : Instant.now().plus(DEFAULT_LIFETIME);
      return new CachedToken(parsed.accessToken, tokenType, expiresAt);
    }
  }

  private String buildFormBody() {
    final Map<String, String> params = new LinkedHashMap<>();
    params.put("grant_type", "client_credentials");
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    if (audience != null && !audience.isBlank()) {
      params.put("audience", audience);
    }
    if (scope != null && !scope.isBlank()) {
      params.put("scope", scope);
    }
    if (resource != null && !resource.isBlank()) {
      params.put("resource", resource);
    }
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (final Map.Entry<String, String> e : params.entrySet()) {
      if (!first) {
        sb.append('&');
      }
      first = false;
      sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static final class CachedToken {
    private final String accessToken;
    private final String tokenType;
    private final Instant expiresAt;

    CachedToken(final String accessToken, final String tokenType, final Instant expiresAt) {
      this.accessToken = accessToken;
      this.tokenType = tokenType;
      this.expiresAt = expiresAt;
    }

    /** Whether the token is safe to attach to an outgoing request right now. */
    boolean isUsable() {
      return Instant.now().isBefore(expiresAt.minus(USABLE_SAFETY_MARGIN));
    }

    /** The instant at which the proactive rotation should happen. */
    Instant refreshAt() {
      return expiresAt.minus(REFRESH_MARGIN);
    }

    boolean needsProactiveRefresh() {
      return !Instant.now().isBefore(refreshAt());
    }

    /** Delay until the next proactive refresh, clamped to {@code >= 0}. */
    Duration untilRefresh() {
      final Duration d = Duration.between(Instant.now(), refreshAt());
      return d.isNegative() ? Duration.ZERO : d;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class TokenResponse {
    @JsonProperty("access_token")
    String accessToken;

    @JsonProperty("token_type")
    String tokenType;

    @JsonProperty("expires_in")
    long expiresInSeconds;
  }
}
