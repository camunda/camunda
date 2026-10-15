/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The cluster configuration as last written by a coordinator, shared by all coordinators. A
 * coordinator reads it before changing the configuration and writes the change to it before
 * applying it locally, so that a newly elected coordinator continues from the latest change, and a
 * coordinator racing with another one is rejected instead of issuing conflicting changes.
 */
public interface ClusterConfigurationStore {

  /** The expected next change id when no configuration is stored yet. */
  long ABSENT = -1L;

  /** Returns the stored configuration, or empty if none was written yet. */
  CompletableFuture<Optional<CurrentClusterConfiguration>> read();

  /**
   * Replaces the stored configuration, if the {@link
   * io.camunda.zeebe.dynamic.config.state.PhasedChangeState#nextId() next change id} of the stored
   * one is still {@code expectedNextId}, or none is stored and it is {@link #ABSENT}. Otherwise,
   * fails with a {@link
   * io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException}.
   */
  CompletableFuture<Void> compareAndSet(
      long expectedNextId, CurrentClusterConfiguration configuration);

  /** Returns a store kept in memory, for a single member, as used by tests. */
  static ClusterConfigurationStore inMemory() {
    return new InMemoryClusterConfigurationStore();
  }
}
