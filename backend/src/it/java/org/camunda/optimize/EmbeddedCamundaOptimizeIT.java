/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class EmbeddedCamundaOptimizeIT extends AbstractIT {

  @Test
  public void onOptimizeDestroyNoRemainingZombieThreads() throws Exception {
    embeddedOptimizeExtension.stopOptimize();

    final Set<Thread> baseThreadSet = getCurrentThreads();

    final ScheduledExecutorService optimizeApplicationThread = Executors.newSingleThreadScheduledExecutor();
    final CompletableFuture<Set<Thread>> threadsAfterStartup = new CompletableFuture<>();
    final EmbeddedCamundaOptimize embeddedCamundaOptimize = new EmbeddedCamundaOptimize();
    optimizeApplicationThread.execute(() -> {
      try {
        embeddedCamundaOptimize.startOptimize();
        threadsAfterStartup.complete(getCurrentThreads());
        embeddedCamundaOptimize.join();
      } catch (Exception e) {
        log.error("Failed starting Optimize {}.", e.getMessage(), e);
        threadsAfterStartup.cancel(true);
      }
    });

    final Set<Thread> threadsAfterStart = threadsAfterStartup.get(120, TimeUnit.SECONDS);
    embeddedCamundaOptimize.destroyOptimize();

    optimizeApplicationThread.shutdown();
    final boolean terminated = optimizeApplicationThread.awaitTermination(120, TimeUnit.SECONDS);

    assertThat(terminated).isTrue();
    assertThat(threadsAfterStart).isNotEqualTo(baseThreadSet);
    assertThat(getCurrentThreads()).isEqualTo(baseThreadSet);
  }

  @AfterEach
  public void restartEmbeddedOptimize() throws Exception {
    embeddedOptimizeExtension.startOptimize();
  }

  private Set<Thread> getCurrentThreads() {
    return Thread.getAllStackTraces().keySet().stream()
      .filter(thread -> thread.getThreadGroup() != null
        && !thread.getThreadGroup().getName().equals("system")
        && !thread.getThreadGroup().getName().equals("InnocuousThreadGroup")
        && !thread.getName().contains("ForkJoinPool.commonPool-worker")
        && !thread.getName().contains("WebSocketClient")
        && !thread.getName().contains("Keep-Alive-Timer")
        && !thread.getName().contains("MockServer")
        && !thread.getName().contains("globalEventExecutor")
      )
      .collect(Collectors.toSet());
  }

}
