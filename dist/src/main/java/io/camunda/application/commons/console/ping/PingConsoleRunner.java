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
import io.camunda.application.Profile;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
import io.camunda.application.commons.console.ping.PingConsoleTask.LicensePayload;
import io.camunda.service.ManagementServices;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties({ConsolePingConfiguration.class})
@ConditionalOnProperty(prefix = "camunda.console.ping", name = "enabled", havingValue = "true")
public class PingConsoleRunner implements ApplicationRunner, BrokerTopologyListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleRunner.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
  private final ConsolePingConfiguration pingConfiguration;
  private final ManagementServices managementServices;
  private Either<Exception, String> licensePayload;
  private final BrokerTopologyManager brokerTopologyManager;
  private final ApplicationContext applicationContext;

  @Autowired
  public PingConsoleRunner(
      final ConsolePingConfiguration pingConfigurationProperties,
      final ManagementServices managementServices,
      final ApplicationContext applicationContext,
      final BrokerTopologyManager brokerTopologyManager) {
    pingConfiguration = pingConfigurationProperties;
    this.managementServices = managementServices;
    this.applicationContext = applicationContext;
    this.brokerTopologyManager = brokerTopologyManager;
  }

  @Override
  public void run(final ApplicationArguments args) {
    waitForClusterId()
        .thenAccept(this::buildLicensePayload)
        .thenRun(
            () -> {
              final var validationResult = validateConfiguration();
              if (validationResult.isRight()) {
                startPingTask();
              } else {
                LOGGER.error("Configuration validation failed: {}", validationResult.getLeft());
              }
            });
  }

  private void startPingTask() {

    LOGGER.info(
        "Console ping is enabled cluster ID of {}, with endpoint: {}, and period of {}.",
        brokerTopologyManager.getClusterConfiguration().clusterId().get(),
        pingConfiguration.endpoint(),
        pingConfiguration.pingPeriod());
    final var executor = createTaskExecutor();
    executor.scheduleAtFixedRate(
        new PingConsoleTask(pingConfiguration, licensePayload.get()),
        1000,
        pingConfiguration.pingPeriod.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @VisibleForTesting
  protected Either<String, Void> validateConfiguration() {
    if (pingConfiguration.endpoint() == null) {
      return Either.left("Ping endpoint must not be null.");
    }
    if (pingConfiguration.endpoint.getScheme() == null
        || pingConfiguration.endpoint.getHost() == null) {
      return Either.left(
          String.format("Ping endpoint %s must be a valid URI.", pingConfiguration.endpoint));
    }
    if (brokerTopologyManager.getClusterConfiguration().clusterId().get().isBlank()) {
      return Either.left("Cluster ID must not be null or empty.");
    }
    if (pingConfiguration.clusterName() == null || pingConfiguration.clusterName().isBlank()) {
      return Either.left("Cluster name must not be null or empty.");
    }
    if (pingConfiguration.pingPeriod().isZero() || pingConfiguration.pingPeriod().isNegative()) {
      return Either.left("Ping period must be greater than zero.");
    }
    if (pingConfiguration.retry() != null) {
      if (pingConfiguration.retry().getMaxRetries() <= 0) {
        return Either.left("Number of max retries must be greater than zero.");
      }
      if (pingConfiguration.retry().getRetryDelayMultiplier() <= 0) {
        return Either.left("Retry delay multiplier must be greater than zero.");
      }
      if (pingConfiguration.retry().getMaxRetryDelay().isZero()
          || pingConfiguration.retry().getMinRetryDelay().isNegative()) {
        return Either.left("Max retry delay must be greater than zero.");
      }
      if (pingConfiguration.retry().getMinRetryDelay().isZero()
          || pingConfiguration.retry().getMinRetryDelay().isNegative()) {
        return Either.left("Min retry delay must be greater than zero.");
      }
      if (pingConfiguration
              .retry()
              .getMaxRetryDelay()
              .compareTo(pingConfiguration.retry().getMinRetryDelay())
          < 0) {
        return Either.left("Max retry delay must be greater than or equal to min retry delay.");
      }
    }
    if (licensePayload.isLeft()) {
      return Either.left(
          "Failed to parse license payload for Console ping task: "
              + licensePayload.getLeft().getMessage());
    }
    return Either.right(null);
  }

  private List<String> getActiveProfiles() {
    return Arrays.stream(applicationContext.getEnvironment().getActiveProfiles())
        .filter(
            profile ->
                profile.equals(Profile.BROKER.getId())
                    || profile.equals(Profile.GATEWAY.getId())
                    || profile.equals(Profile.OPERATE.getId())
                    || profile.equals(Profile.TASKLIST.getId())
                    || profile.equals(Profile.STANDALONE.getId()))
        .toList();
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

  private void buildLicensePayload(final String clusterId) {
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
            clusterId,
            pingConfiguration.clusterName(),
            VersionUtil.getVersion(),
            getActiveProfiles(),
            pingConfiguration.properties());
    try {
      licensePayload = Either.right(objectMapper.writeValueAsString(payload));
    } catch (final JsonProcessingException exception) {
      licensePayload = Either.left(exception);
    }
  }

  private CompletableFuture<String> waitForClusterId() {
    final CompletableFuture<String> future = new CompletableFuture<>();
    if (brokerTopologyManager.getClusterConfiguration().clusterId().isPresent()) {
      future.complete(brokerTopologyManager.getClusterConfiguration().clusterId().get());
    } else {
      brokerTopologyManager.addTopologyListener(
          new BrokerTopologyListener() {
            @Override
            public void completedClusterChange() {
              if (brokerTopologyManager.getClusterConfiguration().clusterId().isPresent()) {
                future.complete(brokerTopologyManager.getClusterConfiguration().clusterId().get());
                brokerTopologyManager.removeTopologyListener(this);
              }
            }
          });
    }
    return future;
  }

  @ConfigurationProperties("camunda.console.ping")
  public record ConsolePingConfiguration(
      boolean enabled,
      URI endpoint,
      String clusterName,
      Duration pingPeriod,
      RetryConfiguration retry,
      Map<String, String> properties) {}
}
