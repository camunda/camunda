/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public interface AsyncClosable {

  /**
   * Asynchronous closing. The implementation should close related resources and return a future,
   * which is complete when closing is done.
   *
   * @return the future, which is completed when resources are closed
   */
  ActorFuture<Void> closeAsync();

  static ActorFuture<Void> closeHelper(final AsyncClosable closeable) {
    if (closeable != null) {
      return closeable.closeAsync();
    } else {
      return CompletableActorFuture.completed();
    }
  }
}
