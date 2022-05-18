/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public final class ActorCompatability extends Actor {
  private <T> void awaitFuture(
      final Callable<ActorFuture<T>> callable, final CompletableFuture<T> completableFuture) {
    try {
      callable
          .call()
          .onComplete(
              (t, throwable) -> {
                if (throwable != null) {
                  completableFuture.completeExceptionally(throwable);
                }
                completableFuture.complete(t);
              });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public <T> CompletableFuture<T> await(final Callable<ActorFuture<T>> callable) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    actor.call(() -> awaitFuture(callable, future));
    return future;
  }
}
