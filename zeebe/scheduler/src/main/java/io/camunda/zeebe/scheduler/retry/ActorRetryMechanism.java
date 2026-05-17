/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import io.camunda.zeebe.scheduler.ActorMetrics.ActorMetricsScoped;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class ActorRetryMechanism {
  private OperationToRetry currentCallable;
  private BooleanSupplier currentTerminateCondition;
  private ActorFuture<Boolean> currentFuture;
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private final ActorMetricsScoped metrics;

  public ActorRetryMechanism(final ActorMetricsScoped metrics) {
    this.metrics = metrics;
  }

  void wrap(
      final OperationToRetry callable,
      final BooleanSupplier condition,
      final ActorFuture<Boolean> resultFuture) {
    currentCallable = callable;
    currentTerminateCondition = condition;
    currentFuture = resultFuture;
    retryCount.set(0);
  }

  Control run() throws Exception {
    boolean successOrTerminate = false;
    try{
      if (currentCallable.run()) {
      currentFuture.complete(true);
      successOrTerminate = true;
      return Control.DONE;
      } else if (currentTerminateCondition.getAsBoolean()) {
      currentFuture.complete(false);
      successOrTerminate = true;
      return Control.DONE;
      } else {
      successOrTerminate = false;
      return Control.RETRY;
      }
    } finally {
      if (!successOrTerminate) {
        retryCount.incrementAndGet();
        if (metrics != null) {
          metrics.updateRetryCount(retryCount.get());
        }
      }
    }
  }

  public int getRetryCount() {
    return retryCount.get();
  }

  public void incrementRetryCount() {
    retryCount.incrementAndGet();
  }

  enum Control {
    RETRY,
    DONE;
  }
}
