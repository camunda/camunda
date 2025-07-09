/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
import io.camunda.application.commons.console.ping.PingConsoleTask.LicensePayload;
import io.camunda.service.ManagementServices;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties({ConsolePingConfiguration.class})
@ConditionalOnProperty(prefix = "camunda.console.ping", name = "enabled", havingValue = "true")
public class PingConsoleRunner implements ApplicationRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleRunner.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
  private final ConsolePingConfiguration pingConfiguration;
  private final ManagementServices managementServices;
  private final Either<Exception, String> licensePayload;

  @Autowired
  public PingConsoleRunner(
      final ConsolePingConfiguration pingConfigurationProperties,
      final ManagementServices managementServices) {
    pingConfiguration = pingConfigurationProperties;
    this.managementServices = managementServices;
    licensePayload = getLicensePayload();
  }

  @Override
  public void run(final ApplicationArguments args) {
    try {
      validateConfiguration();
      LOGGER.info(
          "Console ping is enabled with endpoint: {}, and delay of {}.",
          pingConfiguration.endpoint(),
          pingConfiguration.pingPeriod());
      final var executor = createTaskExecutor();
      executor.scheduleAtFixedRate(
          new PingConsoleTask(pingConfiguration, licensePayload.get()),
          1000,
          pingConfiguration.pingPeriod.toMillis(),
          TimeUnit.MILLISECONDS);
    } catch (final Exception exception) {
      LOGGER.error("Failed to initialize PingConsoleTask.", exception);
    }
  }

  @VisibleForTesting
  protected void validateConfiguration() {
    if (pingConfiguration.endpoint() == null) {
      throw new IllegalArgumentException("Ping endpoint must not be null.");
    }
    if (pingConfiguration.endpoint.getScheme() == null
        || pingConfiguration.endpoint.getHost() == null) {
      throw new IllegalArgumentException(
          String.format("Ping endpoint %s must be a valid URI.", pingConfiguration.endpoint));
    }
    if (pingConfiguration.clusterId() == null || pingConfiguration.clusterId().isBlank()) {
      throw new IllegalArgumentException("Cluster ID must not be null or empty.");
    }
    if (pingConfiguration.clusterName() == null || pingConfiguration.clusterName().isBlank()) {
      throw new IllegalArgumentException("Cluster name must not be null or empty.");
    }
    if (pingConfiguration.pingPeriod().isZero() || pingConfiguration.pingPeriod().isNegative()) {
      throw new IllegalArgumentException("Ping period must be greater than zero.");
    }
    if (pingConfiguration.retry() != null) {
      if (pingConfiguration.retry().getMaxRetries() <= 0) {
        throw new IllegalArgumentException("Number of max retries must be greater than zero.");
      }
      if (pingConfiguration.retry().getRetryDelayMultiplier() <= 0) {
        throw new IllegalArgumentException("Retry delay multiplier must be greater than zero.");
      }
      if (pingConfiguration.retry().getMaxRetryDelay().isZero()
          || pingConfiguration.retry().getMinRetryDelay().isNegative()) {
        throw new IllegalArgumentException("Max retry delay must be greater than zero.");
      }
      if (pingConfiguration.retry().getMinRetryDelay().isZero()
          || pingConfiguration.retry().getMinRetryDelay().isNegative()) {
        throw new IllegalArgumentException("Min retry delay must be greater than zero.");
      }
      if (pingConfiguration
              .retry()
              .getMaxRetryDelay()
              .compareTo(pingConfiguration.retry().getMinRetryDelay())
          < 0) {
        throw new IllegalArgumentException(
            "Max retry delay must be greater than or equal to min retry delay.");
      }
    }
    if (licensePayload.isLeft()) {
      throw new IllegalArgumentException(
          "Failed to parse license payload for Console ping task.", licensePayload.getLeft());
    }
  }

  public ScheduledThreadPoolExecutor createTaskExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("console-license-ping-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(LOGGER))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(1, threadFactory);
    // if the executor is shut down, there is no need to continue executing existing tasks
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private Either<Exception, String> getLicensePayload() {
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
            pingConfiguration.clusterId(),
            pingConfiguration.clusterName(),
            pingConfiguration.properties());
    try {
      return Either.right(objectMapper.writeValueAsString(payload));
    } catch (final JsonProcessingException exception) {
      return Either.left(exception);
    }
  }

  @ConfigurationProperties("camunda.console.ping")
  public record ConsolePingConfiguration(
      boolean enabled,
      URI endpoint,
      String clusterId,
      String clusterName,
      Duration pingPeriod,
      RetryConfiguration retry,
      Map<String, String> properties) {}
}
