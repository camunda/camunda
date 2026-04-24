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
import java.util.function.BiConsumer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * A self-contained OAuth 2.0 client-credentials grant provider.
 *
 * <p>Fetches an access token from the configured authorization server and caches it in memory until
 * shortly before its expiry. It is intentionally minimal and has no dependency on the
 * camunda-client library.
 */
public final class DefaultOAuthCredentialsProvider implements OAuthCredentialsProvider {

  static final String AUTHORIZATION_HEADER = "Authorization";
  static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

  /** Refresh the token a bit before its actual expiry to avoid using a stale token in flight. */
  private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
  }

  @Override
  public void applyCredentials(final BiConsumer<String, String> headerConsumer) throws IOException {
    final CachedToken token = obtainToken();
    headerConsumer.accept(AUTHORIZATION_HEADER, token.tokenType + " " + token.accessToken);
  }

  private CachedToken obtainToken() throws IOException {
    final CachedToken current = cachedToken;
    if (current != null && current.isStillValid()) {
      return current;
    }
    synchronized (tokenLock) {
      if (cachedToken != null && cachedToken.isStillValid()) {
        return cachedToken;
      }
      final CachedToken fetched = fetchToken();
      cachedToken = fetched;
      return fetched;
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

    try (final CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        final CloseableHttpResponse response = httpClient.execute(post)) {
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
              : Instant.now().plus(Duration.ofMinutes(5));
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

    boolean isStillValid() {
      return Instant.now().isBefore(expiresAt.minus(EXPIRY_SAFETY_MARGIN));
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
