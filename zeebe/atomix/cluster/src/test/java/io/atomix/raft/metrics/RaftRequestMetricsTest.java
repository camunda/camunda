/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@AutoCloseResources
public class RaftRequestMetricsTest {

  @AutoCloseResource private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  @AutoCloseResource private ExecutorService executorService = Executors.newWorkStealingPool();
  private final RaftRequestMetrics raftMetrics = new RaftRequestMetrics("1", meterRegistry);

  private final AtomicBoolean failed = new AtomicBoolean(false);

  @Test
  public void shouldBeThreadSafe() {
    // given
    final var tasks = new ArrayList<Runnable>(20_000);
    for (int i = 0; i < 20_000; i++) {
      final var iFinal = i;
      tasks.add(
          () -> {
            try {
              raftMetrics.receivedMessage(String.valueOf(iFinal % 100));
            } catch (final Exception e) {
              failed.set(true);
            }
          });
      tasks.add(
          () -> {
            try {
              raftMetrics.sendMessage(String.valueOf(iFinal % 5), String.valueOf(iFinal % 100));
            } catch (final Exception e) {
              failed.set(true);
            }
          });
    }
    // when
    for (final var task : tasks) {
      executorService.submit(task);
    }
    // shutdown the executor and wait for all tasks
    executorService.shutdown();
    // then
    assertThat(failed.get()).isFalse();
  }
}
