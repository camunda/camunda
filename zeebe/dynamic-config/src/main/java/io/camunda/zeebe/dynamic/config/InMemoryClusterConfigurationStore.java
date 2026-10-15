/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class InMemoryClusterConfigurationStore implements ClusterConfigurationStore {
  private CurrentClusterConfiguration stored;

  @Override
  public synchronized CompletableFuture<Optional<CurrentClusterConfiguration>> read() {
    return CompletableFuture.completedFuture(Optional.ofNullable(stored));
  }

  @Override
  public synchronized CompletableFuture<Void> compareAndSet(
      final long expectedNextId, final CurrentClusterConfiguration configuration) {
    final var storedNextId = stored == null ? ABSENT : stored.phasedChangeState().nextId();
    if (storedNextId != expectedNextId) {
      return CompletableFuture.failedFuture(
          new ConcurrentModificationException(
              "Expected the stored configuration's next change id to be %d, but it is %d"
                  .formatted(expectedNextId, storedNextId)));
    }
    stored = configuration;
    return CompletableFuture.completedFuture(null);
  }
}
