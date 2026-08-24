/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.cleanup;

import io.camunda.process.test.api.DataDeletionMode;

public final class DefaultCleanupStrategyFactory implements CleanupStrategyFactory {

  @Override
  public CleanupStrategy create(final DataDeletionMode dataDeletionMode) {
    if (dataDeletionMode == null) {
      return new NoneCleanupStrategy();
    }

    switch (dataDeletionMode) {
      case CLUSTER_PURGE:
        return new ClusterPurgeCleanupStrategy();
      case NONE:
        return new NoneCleanupStrategy();
      default:
        return new NoneCleanupStrategy();
    }
  }
}
