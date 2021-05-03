/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.bootstrap;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.monitoring.BrokerStepMetrics;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public final class StartProcess {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<StartStep> startSteps;
  private final CloseProcess closeProcess;
  private final String name;
  private final BrokerStepMetrics brokerStepMetrics;

  public StartProcess(final String name) {
    this.name = name;
    startSteps = new ArrayList<>();
    brokerStepMetrics = new BrokerStepMetrics();
    closeProcess = new CloseProcess(name, brokerStepMetrics);
  }

  public void addStep(final String name, final CheckedRunnable runnable) {
    startSteps.add(
        new StartStep(
            name,
            () -> {
              runnable.run();
              return () -> {};
            }));
  }

  public void addStep(final String name, final StartFunction startFunction) {
    startSteps.add(new StartStep(name, startFunction));
  }

  public CloseProcess start() throws Exception {
    final long durationTime = takeDuration(this::startStepByStep);
    LOG.info(
        "Bootstrap {} succeeded. Started {} steps in {} ms.",
        name,
        startSteps.size(),
        durationTime);
    return closeProcess;
  }

  private void startStepByStep() throws Exception {
    int index = 1;
    for (final StartStep step : startSteps) {
      LOG.info("Bootstrap {} [{}/{}]: {}", name, index, startSteps.size(), step.getName());
      try {
        final long durationStepStarting =
            takeDuration(
                () -> {
                  final AutoCloseable closer = step.getStartFunction().start();
                  closeProcess.addCloser(step.getName(), closer);
                });
        brokerStepMetrics.observeDurationForStarStep(step.getName(), durationStepStarting);
        LOG.debug(
            "Bootstrap {} [{}/{}]: {} started in {} ms",
            name,
            index,
            startSteps.size(),
            step.getName(),
            durationStepStarting);
      } catch (final Exception startException) {
        LOG.error(
            "Bootstrap {} [{}/{}]: {} failed with unexpected exception.",
            name,
            index,
            startSteps.size(),
            step.getName(),
            startException);
        // we need to clean up the already started resources
        closeProcess.closeReverse();
        throw startException;
      }
      index++;
    }
  }

  static long takeDuration(final CheckedRunnable runner) throws Exception {
    final long startTime = System.currentTimeMillis();
    runner.run();
    final long endTime = System.currentTimeMillis();
    return endTime - startTime;
  }

  private static class StartStep {

    private final String name;
    private final StartFunction startFunction;

    StartStep(final String name, final StartFunction startFunction) {
      this.name = name;
      this.startFunction = startFunction;
    }

    String getName() {
      return name;
    }

    StartFunction getStartFunction() {
      return startFunction;
    }
  }
}
