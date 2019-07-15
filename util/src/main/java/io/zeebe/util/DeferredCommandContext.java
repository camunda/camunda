/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

public class DeferredCommandContext {
  protected final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue;
  protected final Consumer<Runnable> cmdConsumer = Runnable::run;

  public DeferredCommandContext() {
    this.cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
  }

  public <T> CompletableFuture<T> runAsync(Consumer<CompletableFuture<T>> action) {
    final CompletableFuture<T> future = new CompletableFuture<>();

    cmdQueue.add(
        () -> {
          try {
            action.accept(future);
          } catch (Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  /** Use this when no future is required. */
  public void runAsync(Runnable r) {
    cmdQueue.add(r);
  }

  public void doWork() {
    while (!cmdQueue.isEmpty()) {
      final Runnable runnable = cmdQueue.poll();
      if (runnable != null) {
        cmdConsumer.accept(runnable);
      }
    }
  }
}
