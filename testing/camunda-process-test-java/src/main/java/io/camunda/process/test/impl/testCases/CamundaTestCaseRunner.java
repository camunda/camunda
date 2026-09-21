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
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.instructions.CreateProcessInstanceInstruction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaTestCaseRunner implements TestCaseRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaTestCaseRunner.class);

  private static final List<String> MOCK_JOB_WORKER_INSTRUCTIONS =
      Arrays.asList(
          TestCaseInstructionType.MOCK_JOB_WORKER_COMPLETE_JOB,
          TestCaseInstructionType.MOCK_JOB_WORKER_THROW_BPMN_ERROR);

  private final CamundaProcessTestContext context;
  private final AssertionFacade assertionFacade;
  private final TestCaseInstructionHandlerRegistry registry;
  private final List<Long> isolatedProcessInstanceKeys = new ArrayList<>();

  public CamundaTestCaseRunner(final CamundaProcessTestContext context) {
    this(context, new TestCaseAssertionFacade());
  }

  public CamundaTestCaseRunner(
      final CamundaProcessTestContext context, final AssertionFacade assertionFacade) {
    this.context = context;
    this.assertionFacade = assertionFacade;
    registry = new TestCaseInstructionHandlerRegistry(isolatedProcessInstanceKeys::add);
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
    warnIfReservedJobsAreMocked(testCase);
    isolatedProcessInstanceKeys.clear();

    try (final CamundaClient camundaClient = context.createClient()) {

      try {
        testCase
            .getInstructions()
            .forEach(instruction -> executeInstruction(instruction, camundaClient));

      } finally {
        cancelIsolatedProcessInstances(camundaClient);
      }

    } finally {
      final Duration duration = Duration.between(start, Instant.now());
      LOGGER.debug("Finished test case: '{}' (duration: {})", testCase.getName(), duration);
    }
  }

  /**
   * A reserved job is hidden from every job worker, including the mock workers this test case
   * opens, so such a test case waits for a job that is never mocked and fails on the await timeout
   * instead of saying why.
   */
  private void warnIfReservedJobsAreMocked(final TestCase testCase) {
    if (reservesJobsAndMocksJobWorkers(testCase)) {
      LOGGER.warn(
          "Test case '{}' reserves a process instance's jobs and mocks a job worker. A reserved job"
              + " is hidden from every job worker, including the mock. If the mock is meant to"
              + " serve the reserved instance, use COMPLETE_JOB or THROW_BPMN_ERROR_FROM_JOB"
              + " instead; otherwise the test case waits for a job nobody takes.",
          testCase.getName());
    }
  }

  static boolean reservesJobsAndMocksJobWorkers(final TestCase testCase) {
    final boolean reservesJobs =
        testCase.getInstructions().stream()
            .filter(CreateProcessInstanceInstruction.class::isInstance)
            .map(CreateProcessInstanceInstruction.class::cast)
            .anyMatch(CreateProcessInstanceInstruction::getReserveJobs);

    final boolean mocksJobWorkers =
        testCase.getInstructions().stream()
            .map(TestCaseInstruction::getType)
            .anyMatch(MOCK_JOB_WORKER_INSTRUCTIONS::contains);

    return reservesJobs && mocksJobWorkers;
  }

  /**
   * An isolated instance waits on jobs no worker takes, so it parks forever unless the test case
   * ended it. Cancelling is a no-op for an instance that already completed.
   */
  private void cancelIsolatedProcessInstances(final CamundaClient camundaClient) {
    isolatedProcessInstanceKeys.forEach(
        processInstanceKey -> {
          try {
            camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
          } catch (final Exception e) {
            LOGGER.debug(
                "Could not cancel the isolated process instance '{}'. It may have ended already.",
                processInstanceKey,
                e);
          }
        });
  }

  private void executeInstruction(
      final TestCaseInstruction instruction, final CamundaClient camundaClient) {
    LOGGER.debug("Executing instruction: {}", instruction);

    try {
      registry.dispatch(instruction, context, camundaClient, assertionFacade);

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
