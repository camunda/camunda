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
  private <T> CompletableFuture<T> awaitFuture(final Callable<ActorFuture<T>> callable) {
    final var completableFuture = new CompletableFuture<T>();
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
    return completableFuture;
  }

  public <T> CompletableFuture<T> await(final Callable<ActorFuture<T>> callable) {
    return actor.call(() -> awaitFuture(callable)).join();
  }
}
