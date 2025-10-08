/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataDirectorySetupStep extends AbstractBrokerStartupStep {
  private static final Logger LOG = LoggerFactory.getLogger(DataDirectorySetupStep.class);

  @Override
  public String getName() {
    return "DataDirectorySetupStep";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    try {
      final var dataCfg = brokerStartupContext.getBrokerConfiguration().getData();
      final var rootDataDirectory = dataCfg.getRootDirectory();

      // Create the appropriate strategy based on configuration
      final var strategy =
          DataDirectoryInitializationStrategyFactory.createStrategy(
              dataCfg.getInitializationMode());

      LOG.info("Using data directory initialization strategy: {}", strategy.getStrategyName());

      // Initialize the data directory using the selected strategy
      final long currentNodeVersion =
          brokerStartupContext.getBrokerConfiguration().getCluster().getNodeIdVersion();

      final var initializedDataDirectory =
          strategy.initializeDataDirectory(
              rootDataDirectory,
              brokerStartupContext.getBrokerConfiguration().getCluster().getNodeId(),
              currentNodeVersion);
      brokerStartupContext
          .getBrokerConfiguration()
          .getData()
          .setBrokerDataDirectory(initializedDataDirectory);

      startupFuture.complete(brokerStartupContext);
    } catch (final Exception e) {
      LOG.error("Failed to setup data directory", e);
      startupFuture.completeExceptionally(e);
    }
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    // No cleanup needed for data directory setup
    shutdownFuture.complete(brokerShutdownContext);
  }

  private Path getDataDirectoryPrefix(final BrokerStartupContext brokerStartupContext) {
    final var dataCfg = brokerStartupContext.getBrokerConfiguration().getData();

    // For USE_PRECONFIGURED_DIRECTORY mode, return the configured directory directly
    if (dataCfg.getInitializationMode()
        == io.camunda.zeebe.broker.system.configuration.DataDirectoryInitializationMode
            .USE_PRECONFIGURED_DIRECTORY) {
      return Paths.get(dataCfg.getRootDirectory());
    }

    // For COPY_FROM_PREVIOUS_VERSION mode, return the versioned directory structure
    return Paths.get(
        dataCfg.getRootDirectory(),
        String.valueOf(brokerStartupContext.getBrokerConfiguration().getCluster().getNodeId()));
  }

  private Path getDataDirectory(
      final BrokerStartupContext brokerStartupContext, final Path dataDirectoryPrefix) {
    final var dataCfg = brokerStartupContext.getBrokerConfiguration().getData();

    // For USE_PRECONFIGURED_DIRECTORY mode, use the prefix as the actual directory
    if (dataCfg.getInitializationMode()
        == io.camunda.zeebe.broker.system.configuration.DataDirectoryInitializationMode
            .USE_PRECONFIGURED_DIRECTORY) {
      return dataDirectoryPrefix;
    }

    // For COPY_FROM_PREVIOUS_VERSION mode, append the version number
    final long currentNodeVersion =
        brokerStartupContext.getBrokerConfiguration().getCluster().getNodeIdVersion();
    return dataDirectoryPrefix.resolve(String.valueOf(currentNodeVersion));
  }
}
