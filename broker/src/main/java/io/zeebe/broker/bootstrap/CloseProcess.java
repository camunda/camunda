/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.bootstrap;

import static io.zeebe.broker.bootstrap.StartProcess.takeDuration;

import io.zeebe.broker.Loggers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public class CloseProcess implements AutoCloseable {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<CloseStep> closeableSteps;
  private final String name;

  CloseProcess(String name) {
    this.name = name;
    this.closeableSteps = new ArrayList<>();
  }

  void addCloser(String name, AutoCloseable closingFunction) {
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
    } catch (Exception willNeverHappen) {
      LOG.error("Unexpected exception occured on closing {}", name, willNeverHappen);
    }
  }

  private void closingStepByStep() {
    int index = 1;

    for (CloseStep closeableStep : closeableSteps) {
      try {
        LOG.info(
            "Closing {} [{}/{}]: {}", name, index, closeableSteps.size(), closeableStep.getName());
        final long durationStepStarting =
            takeDuration(() -> closeableStep.getClosingFunction().close());
        LOG.debug(
            "Closing {} [{}/{}]: {} closed in {} ms",
            name,
            index,
            closeableSteps.size(),
            closeableStep.getName(),
            durationStepStarting);
      } catch (Exception exceptionOnClose) {
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

    CloseStep(String name, AutoCloseable closingFunction) {
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
