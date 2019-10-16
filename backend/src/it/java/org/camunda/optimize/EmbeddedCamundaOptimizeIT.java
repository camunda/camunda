/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class EmbeddedCamundaOptimizeIT {

  @RegisterExtension
  @Order(1)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(2)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void onOptimizeDestroyNoRemainingZombieThreads() throws Exception {
    embeddedOptimizeExtensionRule.stopOptimize();

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
        e.printStackTrace();
      }
    });

    final Set<Thread> threadsAfterStart = threadsAfterStartup.get(30, TimeUnit.SECONDS);
    embeddedCamundaOptimize.destroyOptimize();

    optimizeApplicationThread.shutdown();
    optimizeApplicationThread.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(threadsAfterStart, is(not(baseThreadSet)));
    assertThat(getCurrentThreads(), is(baseThreadSet));
  }

  @AfterEach
  public void restartEmbeddedOptimize() throws Exception {
    embeddedOptimizeExtensionRule.startOptimize();
  }

  private Set<Thread> getCurrentThreads() {
    return Thread.getAllStackTraces().keySet().stream()
      .filter(thread -> thread.getThreadGroup() != null
        && !thread.getName().contains("ForkJoinPool.commonPool-worker")
        && !thread.getThreadGroup().getName().equals("system")
        && !thread.getName().contains("WebSocketClient")
      )
      .collect(Collectors.toSet());
  }

}
