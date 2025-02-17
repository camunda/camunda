/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.notifier;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.exporter.exceptions.IncidentNotifierException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;

/** Class to get and cache M2M Auth0 access token. */
public class M2mTokenManager {

  protected static final String FIELD_NAME_GRANT_TYPE = "grant_type";
  protected static final String GRANT_TYPE_VALUE = "client_credentials";
  protected static final String FIELD_NAME_CLIENT_ID = "client_id";
  protected static final String FIELD_NAME_CLIENT_SECRET = "client_secret";
  protected static final String FIELD_NAME_AUDIENCE = "audience";
  protected static final String FIELD_NAME_ACCESS_TOKEN = "access_token";

  private final ObjectMapper mapper;
  private final ObjectWriter objectWriter;
  private final ObjectReader objectReader;
  private final IncidentNotifierConfiguration configuration;
  private final HttpClient client;

  /** Cached token. */
  private String token;

  private Date tokenExpiresAt;
  private final Object cacheLock = new Object();

  public M2mTokenManager(
      final IncidentNotifierConfiguration configuration,
      final HttpClient client,
      final ObjectMapper mapper) {
    this.configuration = configuration;
    this.mapper = mapper;
    objectWriter = mapper.writer();
    objectReader = mapper.reader();
    this.client = client;
  }

  public String getToken() {
    return getToken(false);
  }

  public String getToken(final boolean forceTokenUpdate) {
    if (token == null || tokenIsExpired() || forceTokenUpdate) {
      synchronized (cacheLock) {
        if (token == null || tokenIsExpired() || forceTokenUpdate) {
          token = getNewToken();
          tokenExpiresAt = JWT.decode(token).getExpiresAt();
        }
      }
    }
    return token;
  }

  private boolean tokenIsExpired() {
    // if tokenExpiresAt == null, we consider the token to be valid and will rely on 401 response
    // code for incident notification
    return tokenExpiresAt != null && !tokenExpiresAt.after(new Date());
  }

  private String getNewToken() {
    try {
      final String tokenURL =
          String.format("https://%s/oauth/token", configuration.getAuth0Domain());

      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(tokenURL))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectWriter.writeValueAsString(createGetTokenRequest())))
              .build();

      final HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IncidentNotifierException(
            String.format(
                "Unable to get the M2M auth token, response status: %s.", response.statusCode()));
      }

      final Map<String, Object> responseBody = objectReader.readValue(response.body(), Map.class);
      return (String) responseBody.get(FIELD_NAME_ACCESS_TOKEN);
    } catch (final IOException | InterruptedException e) {
      throw new IncidentNotifierException("Unable to get the M2M auth token", e);
    }
  }

  private ObjectNode createGetTokenRequest() {
    final ObjectNode request =
        mapper
            .createObjectNode()
            .put(FIELD_NAME_GRANT_TYPE, GRANT_TYPE_VALUE)
            .put(FIELD_NAME_CLIENT_ID, configuration.getM2mClientId())
            .put(FIELD_NAME_CLIENT_SECRET, configuration.getM2mClientSecret())
            .put(FIELD_NAME_AUDIENCE, configuration.getM2mAudience());
    return request;
  }

  /** Only for test usage. */
  protected void clearCache() {
    token = null;
    tokenExpiresAt = null;
  }
}
