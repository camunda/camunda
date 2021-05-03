/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.bootstrap;

import static io.zeebe.broker.bootstrap.StartProcess.takeDuration;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.monitoring.BrokerStepMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public final class CloseProcess implements AutoCloseable {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<CloseStep> closeableSteps;
  private final String name;
  private final BrokerStepMetrics brokerStepMetrics;

  CloseProcess(final String name, final BrokerStepMetrics brokerStepMetrics) {
    this.name = name;
    this.brokerStepMetrics = brokerStepMetrics;
    closeableSteps = new ArrayList<>();
  }

  void addCloser(final String name, final AutoCloseable closingFunction) {
    closeableSteps.add(new CloseStep(name, closingFunction));
  }

  public void closeReverse() {
    Collections.reverse(closeableSteps);

    try {
      final long durationTime = takeDuration(this::closingStepByStep);
      LOG.info(
          "Closing {} succeeded. Closed {} steps in {} ms.",
          name,
          closeableSteps.size(),
          durationTime);
    } catch (final Exception willNeverHappen) {
      LOG.error("Unexpected exception occured on closing {}", name, willNeverHappen);
    }
  }

  private void closingStepByStep() {
    int index = 1;

    for (final CloseStep closeableStep : closeableSteps) {
      try {
        LOG.info(
            "Closing {} [{}/{}]: {}", name, index, closeableSteps.size(), closeableStep.getName());
        final long durationStepStarting =
            takeDuration(() -> closeableStep.getClosingFunction().close());
        brokerStepMetrics.observeDurationForCloseStep(
            closeableStep.getName(), durationStepStarting);
        LOG.debug(
            "Closing {} [{}/{}]: {} closed in {} ms",
            name,
            index,
            closeableSteps.size(),
            closeableStep.getName(),
            durationStepStarting);
      } catch (final Exception exceptionOnClose) {
        LOG.error(
            "Closing {} [{}/{}]: {} failed to close.",
            name,
            index,
            closeableSteps.size(),
            closeableStep.getName(),
            exceptionOnClose);
        // continue with closing others
      }
      index++;
    }
  }

  @Override
  public void close() {
    closeReverse();
  }

  private static class CloseStep {

    private final String name;
    private final AutoCloseable closingFunction;

    CloseStep(final String name, final AutoCloseable closingFunction) {
      this.name = name;
      this.closingFunction = closingFunction;
    }

    String getName() {
      return name;
    }

    AutoCloseable getClosingFunction() {
      return closingFunction;
    }
  }
}
