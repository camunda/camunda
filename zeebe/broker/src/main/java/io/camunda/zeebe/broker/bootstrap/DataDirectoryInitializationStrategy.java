/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy interface for initializing data directories. Different implementations can provide
 * different approaches for setting up the data directory for a broker node.
 */
public interface DataDirectoryInitializationStrategy {

  /**
   * Initializes the data directory according to the specific strategy implementation.
   *
   * @param dataDirectoryPrefix The base directory containing versioned data directories
   * @param currentNodeVersion The current node version
   * @throws IOException If an error occurs during directory setup
   */
  Path initializeDataDirectory(String rootDataDirectory, int nodeId, long currentNodeVersion)
      throws IOException;

  /**
   * @return The name of this initialization strategy for logging purposes
   */
  String getStrategyName();
}
