/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import java.util.stream.Collectors;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FailedPropertyBasedTestDataPrinter extends TestWatcher {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FailedPropertyBasedTestDataPrinter.class);

  private final PropertyBasedTest propertyBasedTest;

  public FailedPropertyBasedTestDataPrinter(final PropertyBasedTest propertyBasedTest) {
    this.propertyBasedTest = propertyBasedTest;
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    LOGGER.info("Data of failed test case: {}", propertyBasedTest.getDataRecord());
    LOGGER.info(
        "Process of failed test case:{}{}",
        System.lineSeparator(),
        Bpmn.convertToString(propertyBasedTest.getDataRecord().getBpmnModel()));
    LOGGER.info(
        "Execution path of failed test case:{}{}",
        System.lineSeparator(),
        propertyBasedTest.getDataRecord().getExecutionPath().getSteps().stream()
            .map(AbstractExecutionStep::toString)
            .collect(Collectors.joining(System.lineSeparator())));
  }
}
