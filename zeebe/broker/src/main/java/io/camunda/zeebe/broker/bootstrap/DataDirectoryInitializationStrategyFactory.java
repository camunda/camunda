/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.configuration.DataDirectoryInitializationMode;

/**
 * Factory class responsible for creating the appropriate data directory initialization strategy
 * based on the configuration.
 */
public final class DataDirectoryInitializationStrategyFactory {

  private DataDirectoryInitializationStrategyFactory() {
    // Utility class
  }

  /**
   * Creates the appropriate data directory initialization strategy based on the provided mode.
   *
   * @param mode The initialization mode from configuration
   * @return The corresponding strategy implementation
   */
  public static DataDirectoryInitializationStrategy createStrategy(
      final DataDirectoryInitializationMode mode) {
    return switch (mode) {
      case SHARED_ROOT_VERSIONED_NODE -> new CopyFromPreviousVersionStrategy();
      case USE_PRECONFIGURED_DIRECTORY -> new UsePreConfiguredDirectoryStrategy();
    };
  }
}
