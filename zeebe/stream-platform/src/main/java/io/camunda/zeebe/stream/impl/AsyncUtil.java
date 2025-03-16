/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.function.Consumer;

public class AsyncUtil {
  public static ActorFuture<Void> chainSteps(final int index, final Step[] steps) {
    if (index >= steps.length) {
      return CompletableActorFuture.completed(null);
    }

    final Step step = steps[index];
    final ActorFuture<Void> future = step.run();

    final int nextIndex = index + 1;
    if (nextIndex < steps.length) {
      future.onComplete(
          (v, t) -> {
            if (t == null) {
              chainSteps(nextIndex, steps);
            } else {
              future.completeExceptionally(t);
            }
          });
    }
    return future;
  }

  public static void chainSteps(
      final int index,
      final Step[] steps,
      final Runnable last,
      final Consumer<Throwable> onFailure) {
    if (index == steps.length) {
      last.run();
      return;
    }

    final Step step = steps[index];
    step.run()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                chainSteps(index + 1, steps, last, onFailure);
              } else {
                onFailure.accept(t);
              }
            });
  }

  public interface Step {
    ActorFuture<Void> run();
  }
}
