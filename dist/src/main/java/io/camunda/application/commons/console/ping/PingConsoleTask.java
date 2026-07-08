/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
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

public class PingConsoleTask implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleTask.class);
  private static final int MAX_RESPONSE_BODY_LENGTH = 1000;
  private final HttpClient client;
  private final ConsolePingConfiguration pingConfiguration;
  private final RetryDecorator retryDecorator;
  private final String licensePayload;

  @VisibleForTesting
  public PingConsoleTask(
      final ConsolePingConfiguration pingConfiguration,
      final HttpClient client,
      final String licensePayload) {
    this.pingConfiguration = pingConfiguration;
    this.client = client;
    retryDecorator =
        new RetryDecorator(
            pingConfiguration.retry() != null
                ? pingConfiguration.retry()
                : new RetryConfiguration());
    this.licensePayload = licensePayload;
  }

  public PingConsoleTask(
      final ConsolePingConfiguration pingConfiguration, final String licensePayload) {
    this(pingConfiguration, HttpClient.newHttpClient(), licensePayload);
  }

  @Override
  public void run() {
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(pingConfiguration.endpoint())
              .header("Accept", "application/json")
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(licensePayload))
              .build();

      retryDecorator.decorate("Ping console.", () -> tryPingConsole(request));

    } catch (final Exception e) {
      LOGGER.warn("Failed to execute Console ping task. Exception Message: {}", e.getMessage(), e);
    }
  }

  @VisibleForTesting
  protected void tryPingConsole(final HttpRequest request) throws RetriableException {
    final HttpResponse<String> resp;
    try {
      resp = client.send(request, BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      // If the request fails due to a network issue, we should retry.
      throw new RetriableException("Network error: " + e.getMessage());
    }

    // We should retry on server errors, timeouts or too many request.
    if (resp.statusCode() >= 500) {
      LOGGER.debug("Received server error: {}. A retry will be attempted.", resp.statusCode());
      throw new RetriableException("Server error: " + resp.statusCode(), resp.body());
    } else if (resp.statusCode() == 429 || resp.statusCode() == 408) {
      LOGGER.debug("Received client error: {}. A retry will be attempted.", resp.statusCode());
      throw new RetriableException(
          "Too many requests or timeout: " + resp.statusCode(), resp.body());
    } else if (resp.statusCode() >= 400) {
      // Should not retry for the remaining 4xx errors, but we log them.
      LOGGER.debug(
          "Received client error response: {}. No retry will be attempted.", resp.statusCode());
    }
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
