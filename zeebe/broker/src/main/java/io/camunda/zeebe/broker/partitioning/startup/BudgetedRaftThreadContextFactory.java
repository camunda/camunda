/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.atomix.raft.RaftThreadContextFactory;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.scheduler.CpuBudget;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Creates the Raft threads of a physical tenant so that their CPU time counts for its {@link
 * CpuBudget}. The threads are charged and never stopped: a Raft thread that stops misses heartbeats
 * and causes elections.
 */
final class BudgetedRaftThreadContextFactory implements RaftThreadContextFactory {
  private final CpuBudget budget;

  BudgetedRaftThreadContextFactory(final CpuBudget budget) {
    this.budget = budget;
  }

  @Override
  public ThreadContext createContext(
      final ThreadFactory factory, final Consumer<Throwable> unCaughtExceptionHandler) {
    return new SingleThreadContext(
        runnable -> {
          final var thread = factory.newThread(runnable);
          budget.register(thread);
          return thread;
        },
        unCaughtExceptionHandler);
  }
}
