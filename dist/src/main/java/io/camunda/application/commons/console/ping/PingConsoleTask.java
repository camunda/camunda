/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.service.ManagementServices;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingConsoleTask implements Runnable {

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleTask.class);
  private static final int NUMBER_OF_MAX_RETRIES = 10;
  private static final int INITIAL_RETRY_DELAY_MS = 500;

  private final ManagementServices managementServices;
  private final PingConfiguration pingConfiguration;
  private final HttpClient client;

  public PingConsoleTask(
      final ManagementServices managementServices, final PingConfiguration pingConfiguration) {
    this.managementServices = managementServices;
    this.pingConfiguration = pingConfiguration;
    client = HttpClient.newHttpClient();
  }

  @Override
  public void run() {

    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(pingConfiguration.getEndpoint()))
              .POST(HttpRequest.BodyPublishers.ofString(getLicensePayload()))
              .build();

      RetryUtil.executeWithRetry(
          () -> tryPingConsole(request), NUMBER_OF_MAX_RETRIES, INITIAL_RETRY_DELAY_MS);

    } catch (final Exception e) {
      if (e instanceof JsonProcessingException) {
        LOGGER.warn("Failed to parse payload for Console ping task: {}", e.getMessage());
      } else {
        LOGGER.warn(
            "Failed to execute Console ping task, after {} retries. Exception Message: {}. {}",
            NUMBER_OF_MAX_RETRIES,
            e.getMessage(),
            e);
      }

      // TODO: later to remove this log also
      LOGGER.info("Running the Console ping task.");
    }
  }

  private HttpResponse<String> tryPingConsole(final HttpRequest request) {
    // TODO: create custome exceptions for the ping task
    try {
      final HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
      // We should retry on server errors, timeouts or too many request.
      if (resp.statusCode() >= 500) {
        throw new RuntimeException("Server error: " + resp.statusCode());
      } else if (resp.statusCode() == 429 || resp.statusCode() == 408) {
        throw new RuntimeException("Too many requests or timeout: " + resp.statusCode());
      } else if (resp.statusCode() >= 400) {
        // Should not retry for the remaining 4xx errors, but we log them.
        LOGGER.warn(
            "Received client error response: {}. No retry will be attempted.", resp.statusCode());
      }
      return resp;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String getLicensePayload() throws JsonProcessingException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final LicensePayload.License license =
        new LicensePayload.License(
            managementServices.isCamundaLicenseValid(),
            managementServices.getCamundaLicenseType().toString(),
            managementServices.isCommercialCamundaLicense(),
            managementServices.getCamundaLicenseExpiresAt() == null
                ? null
                : DATE_TIME_FORMATTER.format(managementServices.getCamundaLicenseExpiresAt()));
    final LicensePayload payload =
        new LicensePayload(
            license,
            pingConfiguration.getClusterId(),
            pingConfiguration.getClusterName(),
            pingConfiguration.getProperties());
    return objectMapper.writeValueAsString(payload);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LicensePayload(
      License license, String clusterId, String clusterName, Map<String, String> properties) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record License(
        boolean validLicense, String licenseType, boolean isCommercial, String expiresAt) {}
  }

  public static class RetryUtil {
    public static <T> T executeWithRetry(
        final Supplier<T> action, final int maxRetries, final long initialDelayMillis)
        throws Exception {
      int attempt = 0;
      while (true) {
        try {
          return action.get();
        } catch (final Exception exception) {
          attempt++;
          if (attempt > maxRetries) {
            throw exception;
          }

          // Exponential backoff with jitter
          final long delay = (long) (initialDelayMillis * Math.pow(2, attempt - 1));
          final long jitter = (long) (Math.random() * 100); // up to 100ms jitter
          final long totalDelay = delay + jitter;

          LOGGER.info(
              "Failed to ping console. Will retry {}/{} after {} ms. Exception: {}",
              attempt,
              maxRetries,
              totalDelay,
              exception.getMessage());
          TimeUnit.MILLISECONDS.sleep(totalDelay);
        }
      }
    }
  }
}
