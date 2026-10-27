/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.application.commons.hub.ping.PingHubRunner.HubPingConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingHubTask implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PingHubTask.class);
  private static final int MAX_RESPONSE_BODY_LENGTH = 1000;
  private final HttpClient client;
  private final HubPingConfiguration pingConfiguration;
  private final RetryDecorator retryDecorator;
  private final String licensePayload;
  private final M2MTokenProvider tokenProvider;

  @VisibleForTesting
  public PingHubTask(
      final HubPingConfiguration pingConfiguration,
      final M2MTokenProvider tokenProvider,
      final HttpClient client,
      final String licensePayload) {
    this.pingConfiguration = pingConfiguration;
    this.tokenProvider = tokenProvider;
    this.client = client;
    retryDecorator =
        new RetryDecorator(
            pingConfiguration.retry() != null
                ? pingConfiguration.retry()
                : new RetryConfiguration());
    this.licensePayload = licensePayload;
  }

  public PingHubTask(
      final HubPingConfiguration pingConfiguration,
      final M2MTokenProvider tokenProvider,
      final String licensePayload) {
    this(pingConfiguration, tokenProvider, HttpClient.newHttpClient(), licensePayload);
  }

  @Override
  public void run() {
    try {
      final String token = tokenProvider.getToken();
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(pingConfiguration.endpoint())
              .header("Accept", "application/json")
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + token)
              .POST(HttpRequest.BodyPublishers.ofString(licensePayload))
              .build();

      retryDecorator.decorate("Ping Hub.", () -> tryPingHub(request));

    } catch (final Exception e) {
      LOGGER.warn("Failed to execute Hub ping task. Exception Message: {}", e.getMessage(), e);
    }
  }

  @VisibleForTesting
  protected void tryPingHub(final HttpRequest request) throws RetriableException {
    final HttpResponse<String> resp;
    try {
      resp = client.send(request, BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      throw new RetriableException("Network error: " + e.getMessage());
    }

    if (resp.statusCode() >= 500) {
      LOGGER.debug("Received server error: {}. A retry will be attempted.", resp.statusCode());
      throw new RetriableException("Server error: " + resp.statusCode(), resp.body());
    } else if (resp.statusCode() == 429 || resp.statusCode() == 408) {
      LOGGER.debug("Received client error: {}. A retry will be attempted.", resp.statusCode());
      throw new RetriableException(
          "Too many requests or timeout: " + resp.statusCode(), resp.body());
    } else if (resp.statusCode() >= 400) {
      LOGGER.warn(
          "Received client error response: {}. No retry will be attempted. Body: {}",
          resp.statusCode(),
          truncate(resp.body()));
    }
  }

  private static String truncate(final String s) {
    return s.length() <= MAX_RESPONSE_BODY_LENGTH
        ? s
        : s.substring(0, MAX_RESPONSE_BODY_LENGTH) + "... [truncated]";
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LicensePayload(
      License license,
      String clusterId,
      String clusterName,
      String version,
      List<String> profiles,
      Map<String, String> properties) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record License(
        boolean validLicense, String licenseType, boolean isCommercial, String expiresAt) {}
  }

  @VisibleForTesting
  static class RetriableException extends RuntimeException {
    public RetriableException(final String message) {
      super(message);
    }

    public RetriableException(final String message, final String responseBody) {
      super(
          message
              + ". Response body: "
              + (responseBody.length() <= MAX_RESPONSE_BODY_LENGTH
                  ? responseBody
                  : responseBody.substring(0, MAX_RESPONSE_BODY_LENGTH) + "... [truncated]"));
    }
  }
}
