/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.randomized;

import io.camunda.process.generator.BpmnGenerator.GeneratedProcess;
import java.util.function.Supplier;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailedRandomProcessTestPrinter extends TestWatcher {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FailedRandomProcessTestPrinter.class);

  private final Supplier<GeneratedProcess> processSupplier;

  public FailedRandomProcessTestPrinter(final Supplier<GeneratedProcess> processSupplier) {
    this.processSupplier = processSupplier;
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    final var process = processSupplier.get();
    LOGGER.info("Seed of failed test case: {}", process.seed());
  }
}
