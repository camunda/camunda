/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.testCases;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaTestCaseRunner implements TestCaseRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaTestCaseRunner.class);

  private final CamundaProcessTestContext context;
  private final AssertionFacade assertionFacade;
  private final TestCaseInstructionHandlerRegistry registry;

  public CamundaTestCaseRunner(final CamundaProcessTestContext context) {
    this(context, new TestCaseAssertionFacade());
  }

  public CamundaTestCaseRunner(
      final CamundaProcessTestContext context, final AssertionFacade assertionFacade) {
    this(context, assertionFacade, TestCaseInstructionHandlerRegistry.defaultRegistry());
  }

  public CamundaTestCaseRunner(
      final CamundaProcessTestContext context,
      final AssertionFacade assertionFacade,
      final TestCaseInstructionHandlerRegistry registry) {
    this.context = context;
    this.assertionFacade = assertionFacade;
    this.registry = registry;
  }

  @Override
  public void run(final TestCase testCase) {
    LOGGER.debug("Running test case: '{}'", testCase.getName());
    final Instant start = Instant.now();

    try (final CamundaClient camundaClient = context.createClient()) {

      testCase
          .getInstructions()
          .forEach(instruction -> executeInstruction(instruction, camundaClient));

    } finally {
      final Duration duration = Duration.between(start, Instant.now());
      LOGGER.debug("Finished test case: '{}' (duration: {})", testCase.getName(), duration);
    }
  }

  private void executeInstruction(
      final TestCaseInstruction instruction, final CamundaClient camundaClient) {
    LOGGER.debug("Executing instruction: {}", instruction);

    //noinspection rawtypes
    final TestCaseInstructionHandler instructionHandler = registry.getHandler(instruction);

    try {
      //noinspection unchecked
      instructionHandler.execute(instruction, context, camundaClient, assertionFacade);

    } catch (final Exception e) {
      throw new TestCaseRunException(
          String.format(
              "Failed to execute instruction '%s': %s", instruction.getType(), instruction),
          e);
    }
  }

  private static final class TestCaseAssertionFacade implements AssertionFacade {

    @Override
    public ProcessInstanceAssert assertThatProcessInstance(final ProcessInstanceSelector selector) {
      return CamundaAssert.assertThatProcessInstance(selector);
    }

    @Override
    public UserTaskAssert assertThatUserTask(final UserTaskSelector selector) {
      return CamundaAssert.assertThatUserTask(selector);
    }

    @Override
    public DecisionInstanceAssert assertThatDecision(final DecisionSelector selector) {
      return CamundaAssert.assertThatDecision(selector);
    }
  }
}
