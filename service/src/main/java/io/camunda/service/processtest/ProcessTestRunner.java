/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import io.camunda.service.processtest.dsl.TestAction;
import io.camunda.service.processtest.dsl.TestCase;
import io.camunda.service.processtest.dsl.TestInstruction;
import io.camunda.service.processtest.dsl.TestResource;
import io.camunda.service.processtest.dsl.TestSpecification;
import io.camunda.service.processtest.dsl.TestVerification;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.inmemory.InMemoryEngine;
import io.camunda.zeebe.engine.inmemory.InMemoryEngineFactory;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

public class ProcessTestRunner {

  private static final Logger LOGGER = Loggers.ENGINE_PROCESSING_LOGGER;

  public TestSpecificationResult run(final TestSpecification testSpecification) {

    final List<TestCaseResult> testResults =
        testSpecification.testCases().stream()
            .map(testCase -> runTestCase(testCase, testSpecification.testResources()))
            .toList();

    return new TestSpecificationResult(testResults);
  }

  private TestCaseResult runTestCase(
      final TestCase testCase, final List<TestResource> testResources) {
    try {

      // 1: bootstrap
      final InMemoryEngine engine = InMemoryEngineFactory.create();
      engine.start();

      final TestContext testContext = new TestContext();
      final ProcessTestActions actions = new ProcessTestActions(engine, Duration.ofSeconds(1));
      final ProcessTestVerifications verifications =
          new ProcessTestVerifications(engine, Duration.ofSeconds(1));

      // 2: deploy resources
      testResources.forEach(
          testResource -> actions.deployProcess(testResource.name(), testResource.resource()));

      // 3: apply instructions
      final VerificationResult result =
          runInstructions(testCase.instructions(), testContext, actions, verifications);

      // 4: collect output

      // 5: shutdown
      engine.stop();

      if (result.isFulfilled()) {
        // return success
        return new TestCaseResult(testCase, true, "<success>");
      } else {
        // return failure
        return new TestCaseResult(testCase, false, result.failureMessage());
      }
    } catch (final Exception e) {
      LOGGER.warn("Failed to run test case.", e);
      return new TestCaseResult(
          testCase, false, "The test case failed with the message: %s".formatted(e.getMessage()));
    }
  }

  private VerificationResult runInstructions(
      final List<TestInstruction> instructions,
      final TestContext testContext,
      final ProcessTestActions actions,
      final ProcessTestVerifications verifications) {

    for (final TestInstruction instruction : instructions) {
      if (instruction instanceof final TestAction action) {
        action.execute(testContext, actions);

      } else if (instruction instanceof final TestVerification verification) {
        final VerificationResult verificationResult =
            verification.verify(testContext, verifications);

        if (!verificationResult.isFulfilled()) {
          return verificationResult;
        }
      }
    }
    return VerificationResult.success();
  }
}
