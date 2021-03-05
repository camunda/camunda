/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public final class TestPartitionStep implements PartitionStep {
  private final boolean failOpen;
  private final boolean failClose;

  private TestPartitionStep(final boolean failOpen, final boolean failClose) {
    this.failOpen = failOpen;
    this.failClose = failClose;
  }

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    return failOpen
        ? CompletableActorFuture.completedExceptionally(new Exception("expected"))
        : CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    return failClose
        ? CompletableActorFuture.completedExceptionally(new Exception("expected"))
        : CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "TestPartitionStep";
  }

  public static TestPartitionStepBuilder builder() {
    return new TestPartitionStepBuilder();
  }

  public static class TestPartitionStepBuilder {
    private boolean failOpen;
    private boolean failClose;

    public TestPartitionStepBuilder failOnOpen() {
      failOpen = true;
      return this;
    }

    public TestPartitionStepBuilder failOnClose() {
      failClose = true;
      return this;
    }

    public TestPartitionStep build() {
      return new TestPartitionStep(failOpen, failClose);
    }
  }
}
