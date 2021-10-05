/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public final class TestPartitionStep implements PartitionStep {
  private final boolean failOpen;
  private final boolean failClose;
  private final RuntimeException throwOnOpen;
  private final RuntimeException throwOnClose;

  private TestPartitionStep(
      final boolean failOpen,
      final boolean failClose,
      final RuntimeException throwOnOpen,
      final RuntimeException throwOnClose) {
    this.failOpen = failOpen;
    this.failClose = failClose;
    this.throwOnOpen = throwOnOpen;
    this.throwOnClose = throwOnClose;
  }

  @Override
  public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
    if (throwOnOpen != null) {
      throw throwOnOpen;
    }

    return failOpen
        ? CompletableActorFuture.completedExceptionally(new Exception("expected"))
        : CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
    if (throwOnClose != null) {
      throw throwOnClose;
    }

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
    private RuntimeException throwOnOpen;
    private RuntimeException throwOnClose;

    public TestPartitionStepBuilder throwOnOpen(final RuntimeException e) {
      throwOnOpen = e;
      return this;
    }

    public TestPartitionStepBuilder throwOnClose(final RuntimeException e) {
      throwOnClose = e;
      return this;
    }

    public TestPartitionStepBuilder failOnOpen() {
      failOpen = true;
      return this;
    }

    public TestPartitionStepBuilder failOnClose() {
      failClose = true;
      return this;
    }

    public TestPartitionStep build() {
      return new TestPartitionStep(failOpen, failClose, throwOnOpen, throwOnClose);
    }
  }
}
