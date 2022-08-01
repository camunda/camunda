/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;

public interface ProcessingScheduleService {

  void runDelayed(Duration delay, Runnable followUpTask);

  <T> void runOnCompletion(ActorFuture<T> precedingTask, BiConsumer<T, Throwable> followUpTask);

  @Deprecated
  <T> void runOnCompletion(ActorFuture<T> precedingTask, LegacyTask followUpTask);

  @Deprecated
  void runDelayed(Duration delay, LegacyTask followUpTask);

  default void runAtFixedRate(final Duration delay, final Runnable task) {
    runDelayed(
        delay,
        () -> {
          try {
            task.run();
          } finally {
            runAtFixedRate(delay, task);
          }
        });
  }
}
