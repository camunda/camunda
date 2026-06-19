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

public interface ModeChangeExecutor {

  ActorFuture<Void> enterRecovery();

  ActorFuture<Void> exitRecovery();

  final class NoopModeChangeExecutor implements ModeChangeExecutor {
    @Override
    public ActorFuture<Void> enterRecovery() {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> exitRecovery() {
      return CompletableActorFuture.completed(null);
    }
  }
}
