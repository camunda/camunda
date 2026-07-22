/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.cleanup;

import io.camunda.process.test.api.DataDeletionMode;

/**
 * Factory interface for creating {@link CleanupStrategy} instances based on the provided {@link
 * DataDeletionMode}.
 */
public interface CleanupStrategyFactory {

  /**
   * Creates a {@link CleanupStrategy} instance based on the provided {@link DataDeletionMode}.
   *
   * @param deletionMode the mode of data deletion
   * @return a {@link CleanupStrategy} instance
   */
  CleanupStrategy create(final DataDeletionMode deletionMode);
}
