/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.test.util.bpmn.random.TestDataGenerator.TestDataRecord;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FailedPropertyBasedTestDataPrinter extends TestWatcher {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FailedPropertyBasedTestDataPrinter.class);

  private final Supplier<TestDataRecord> testDataRecordSupplier;

  public FailedPropertyBasedTestDataPrinter(final Supplier<TestDataRecord> testDataRecordSupplier) {
    this.testDataRecordSupplier = testDataRecordSupplier;
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    final var record = testDataRecordSupplier.get();
    final var currentStep = record.getCurrentStep();

    LOGGER.info("Data of failed test case: {}", record);
    LOGGER.info("Test case failed at: {}", currentStep);

    LOGGER.info(
        "Execution path of failed test case:{}{}",
        System.lineSeparator(),
        record.getExecutionPath().getSteps().stream()
            .map(step -> (step == currentStep ? "--failed here--> " : "") + step.toString())
            .collect(Collectors.joining(System.lineSeparator())));
  }
}
