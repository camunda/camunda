/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/** Enumeration of available data directory initialization modes. */
public enum DataDirectoryInitializationMode {
  /**
   * Copy data from the latest valid previous version, or create empty directory if no valid
   * previous version exists. This is useful when multiple nodes share the same root data directory,
   * but each node has its own versioned subdirectory.
   */
  SHARED_ROOT_VERSIONED_NODE,

  /**
   * Use the pre-configured data directory directly without versioning or copying. Simply ensures
   * the directory exists and creates initialization marker. (This must be default)
   */
  USE_PRECONFIGURED_DIRECTORY
}
