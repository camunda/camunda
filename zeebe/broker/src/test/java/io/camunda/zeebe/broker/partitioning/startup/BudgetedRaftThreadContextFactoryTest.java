/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import static io.atomix.utils.concurrent.Threads.namedThreads;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.scheduler.CpuBudget;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BudgetedRaftThreadContextFactoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BudgetedRaftThreadContextFactoryTest.class);
  private static final long BURN_NANOS = Duration.ofMillis(200).toNanos();

  private final CpuBudget budget = new CpuBudget(1.0, Duration.ofMillis(20));
  private ThreadContext context;

  @BeforeEach
  void setUp() {
    budget.start(List.of());
  }

  @AfterEach
  void tearDown() {
    budget.close();
    if (context != null) {
      context.close();
    }
  }

  @Test
  void shouldChargeCpuTimeOfRaftThreadToBudget() throws Exception {
    // given
    context =
        new BudgetedRaftThreadContextFactory(budget)
            .createContext(namedThreads("raft-test-%d", LOGGER), error -> {});
    final var done = new CountDownLatch(1);

    // when
    context.execute(
        () -> {
          burn();
          done.countDown();
        });

    // then
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    Awaitility.await("until the budget has charged the Raft thread")
        .untilAsserted(() -> assertThat(budget.usedNanos()).isGreaterThan(BURN_NANOS / 2));
  }

  private static void burn() {
    final var bean = java.lang.management.ManagementFactory.getThreadMXBean();
    final long start = bean.getCurrentThreadCpuTime();
    while (bean.getCurrentThreadCpuTime() - start < BURN_NANOS) {
      Thread.onSpinWait();
    }
  }
}
