/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public interface ClusterChangeExecutor {

  /**
   * This method will start an asynchronous deletion of the history storage, returning a future
   * indicating success/failure.
   *
   * <p>When the future is completed successfully, all history data will have been purged. When a
   * future is completed exceptionally, then not all (but none or some) may have been purged.
   *
   * <p>This operation should be idempotent, as it may be retried multiple times until successful.
   *
   * @return future when the operation is completed
   */
  ActorFuture<Void> deleteHistory();

  final class NoopClusterChangeExecutor implements ClusterChangeExecutor {
    @Override
    public ActorFuture<Void> deleteHistory() {
      return CompletableActorFuture.completed(null);
    }
  }
}
