/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.testCases;

/** Runs a test case represented by a {@link TestCase}. */
public interface TestCaseRunner {

  /**
   * Runs the given {@link TestCase} by executing its instructions. If an assertion instruction
   * fails, the assertion error is thrown to the caller. If the test case is executed without
   * errors, it is considered successful and all assertions have passed.
   *
   * @param testCase the test case to run
   * @throws AssertionError if an assertion instruction fails
   */
  void run(TestCase testCase);
}
