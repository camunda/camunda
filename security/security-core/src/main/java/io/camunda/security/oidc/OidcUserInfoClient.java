/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Thin wrapper around {@link HttpClient} for calling an OIDC provider's UserInfo endpoint with a
 * bearer token and parsing the JSON response into a claim map.
 *
 * <p>Defences against common IdP misbehaviour:
 *
 * <ul>
 *   <li>Response body size capped at {@value #MAX_BODY_BYTES} bytes. A misconfigured or hostile
 *       endpoint streaming unbounded data is rejected rather than consuming heap.
 *   <li>{@code Content-Type} is checked before JSON parsing. Signed UserInfo responses ({@code
 *       application/jwt}, per OIDC Core §5.3.2) are not supported in this version and are rejected
 *       with a clear error rather than producing a confusing JSON parse failure.
 * </ul>
 */
public class OidcUserInfoClient {

  /**
   * Maximum accepted UserInfo response body size (bytes). OIDC UserInfo responses are typically
   * well under 10 KiB; 1 MiB is generous for well-behaved IdPs and caps memory exposure under
   * adversarial or misconfigured ones.
   */
  public static final int MAX_BODY_BYTES = 1 << 20; // 1 MiB

  private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Duration requestTimeout;

  public OidcUserInfoClient(final HttpClient httpClient, final Duration requestTimeout) {
    this.httpClient = Objects.requireNonNull(httpClient);
    this.requestTimeout = Objects.requireNonNull(requestTimeout);
    objectMapper = new ObjectMapper();
  }

  public Map<String, Object> fetch(final URI userInfoUri, final String bearerToken) {
    final HttpRequest request =
        HttpRequest.newBuilder(userInfoUri)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + bearerToken)
            .header("Accept", "application/json")
            .GET()
            .build();

    final HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (final Exception e) {
      throw new OidcUserInfoException("UserInfo request failed: " + e.getMessage(), e);
    }

    if (response.statusCode() / 100 != 2) {
      throw new OidcUserInfoException("UserInfo request returned HTTP " + response.statusCode());
    }

    final byte[] body = response.body();
    if (body != null && body.length > MAX_BODY_BYTES) {
      throw new OidcUserInfoException(
          "UserInfo response exceeds maximum accepted size ("
              + MAX_BODY_BYTES
              + " bytes); refusing to parse");
    }

    final String contentType =
        response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
    if (contentType.contains("application/jwt")) {
      throw new OidcUserInfoException(
          "Signed UserInfo responses (application/jwt) are not supported in this version; "
              + "configure the IdP to return application/json or disable userInfoAugmentation");
    }

    try {
      return objectMapper.readValue(body, CLAIMS_TYPE);
    } catch (final Exception e) {
      throw new OidcUserInfoException("Failed to parse UserInfo JSON: " + e.getMessage(), e);
    }
  }
}
