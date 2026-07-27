/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class M2MTokenProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(M2MTokenProvider.class);
  private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 30;
  private static final int MAX_RESPONSE_LOG_LENGTH = 500;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final HttpClient httpClient;
  private final M2MCredentials credentials;
  private volatile String cachedToken;
  private volatile Instant tokenExpiresAt = Instant.EPOCH;

  public M2MTokenProvider(final M2MCredentials credentials) {
    this(credentials, HttpClient.newHttpClient());
  }

  @VisibleForTesting
  public M2MTokenProvider(final M2MCredentials credentials, final HttpClient httpClient) {
    this.credentials = credentials;
    this.httpClient = httpClient;
  }

  public synchronized String getToken() throws IOException, InterruptedException {
    if (cachedToken == null || Instant.now().isAfter(tokenExpiresAt)) {
      refreshToken();
    }
    return cachedToken;
  }

  private void refreshToken() throws IOException, InterruptedException {
    LOGGER.debug("Fetching M2M token from {}", credentials.tokenEndpoint());
    final String requestBody =
        "grant_type=client_credentials"
            + "&client_id="
            + URLEncoder.encode(credentials.clientId(), StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(credentials.clientSecret(), StandardCharsets.UTF_8);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(credentials.tokenEndpoint())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException(
          "Failed to fetch M2M token: HTTP "
              + response.statusCode()
              + ", body: "
              + truncate(response.body()));
    }

    final JsonNode json = OBJECT_MAPPER.readTree(response.body());
    final JsonNode tokenNode = json.path("access_token");
    if (tokenNode.isMissingNode() || tokenNode.isNull()) {
      throw new IOException(
          "Token response missing 'access_token' field. Body: " + truncate(response.body()));
    }
    cachedToken = tokenNode.asText();
    final long expiresIn = json.path("expires_in").asLong(3600);
    tokenExpiresAt = Instant.now().plusSeconds(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);
    LOGGER.debug("Successfully fetched M2M token, expires in {} seconds", expiresIn);
  }

  private static String truncate(final String s) {
    return s.length() <= MAX_RESPONSE_LOG_LENGTH
        ? s
        : s.substring(0, MAX_RESPONSE_LOG_LENGTH) + "... [truncated]";
  }
}
