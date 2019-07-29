/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.rule;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.engine.EnginePluginClient;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Starts optimize during the test execution in maven. Also
 * ensures a clean shutdown of optimize after all tests are done.
 */
@Slf4j
public class StartOptimizeExecutionListener extends RunListener {

  @Override
  public void testRunStarted(Description description) throws Exception {
    // Called before any tests have been run.
    try {
      // deploy default engine for successful Optimize startup
      deployDefaultEngine();
      TestEmbeddedCamundaOptimize.getInstance().start();
    } catch (Exception e) {
      log.error("Failed to start Optimize", e);
    }
    waitUntilOptimizeIsStarted();
  }

  @Override
  public void testRunFinished(Result result) throws Exception {
    // Called when all tests have finished
    TestEmbeddedCamundaOptimize.getInstance().destroy();
  }

  private void deployDefaultEngine() throws IOException {
    try (final CloseableHttpClient minimal = HttpClients.createMinimal()) {
      new EnginePluginClient(minimal).deployEngine(
        IntegrationTestConfigurationUtil.resolveFullDefaultEngineName()
      );
    }
  }

  private void waitUntilOptimizeIsStarted() throws InterruptedException {
    OffsetDateTime timeout = OffsetDateTime.now().plusMinutes(5L);
    while (!TestEmbeddedCamundaOptimize.getInstance().isStarted()) {
      Thread.sleep(100L);
      if (OffsetDateTime.now().isAfter(timeout)) {
        throw new OptimizeIntegrationTestException("Optimize startup shouldn't take longer than 5 minutes");
      }
    }
  }
}