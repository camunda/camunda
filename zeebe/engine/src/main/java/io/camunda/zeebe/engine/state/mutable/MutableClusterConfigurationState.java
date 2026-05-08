/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;

/**
 * Mutable state for the authoritative {@link ClusterConfiguration} stored on the system partition.
 *
 * <p>Implementations persist the configuration to RocksDB and maintain an in-memory cache for cheap
 * reads. Writes must be transactional with the stream-processor's processing state.
 */
public interface MutableClusterConfigurationState {

  /** Returns the latest cluster configuration. Never null; may be {@code uninitialized}. */
  ClusterConfiguration get();

  /** Replace the persisted configuration and refresh the in-memory cache. */
  void put(ClusterConfiguration config);
}
