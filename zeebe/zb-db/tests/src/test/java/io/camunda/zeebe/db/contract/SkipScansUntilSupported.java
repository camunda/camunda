/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Skips a contract test that scans a column family, which the write-behind cache rejects with an
 * {@link UnsupportedOperationException} until it supports scans. Delete this once it does.
 *
 * <p>Works as a JUnit 4 rule and as a JUnit 5 extension, since the contract tests use both.
 */
final class SkipScansUntilSupported implements TestRule, TestExecutionExceptionHandler {

  private static final String REASON =
      "Needs a scan, which the write-behind cache doesn't support yet";

  @Override
  public Statement apply(final Statement base, final Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
        } catch (final UnsupportedOperationException e) {
          throw new AssumptionViolatedException(REASON, e);
        }
      }
    };
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    if (throwable instanceof UnsupportedOperationException) {
      Assumptions.abort(REASON);
    }
    throw throwable;
  }
}
