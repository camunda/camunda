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
package io.camunda.process.test.impl.dsl;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestScenarioRunner;
import io.camunda.process.test.impl.dsl.instructions.AssertDecisionInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertElementInstanceInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertElementInstancesInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertProcessInstanceInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertProcessInstanceMessageSubscriptionInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertUserTaskInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.AssertVariablesInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.BroadcastSignalInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CompleteJobAdHocSubProcessInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CompleteJobInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CompleteJobUserTaskListenerInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CompleteUserTaskInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CorrelateMessageInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.CreateProcessInstanceInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.EvaluateConditionalStartEventInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.EvaluateDecisionInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.IncreaseTimeInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.MockChildProcessInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.MockDmnDecisionInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.MockJobWorkerCompleteJobInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.MockJobWorkerThrowBpmnErrorInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.PublishMessageInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.ResolveIncidentInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.SetTimeInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.ThrowBpmnErrorFromJobInstructionHandler;
import io.camunda.process.test.impl.dsl.instructions.UpdateVariablesInstructionHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaTestScenarioRunner implements TestScenarioRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaTestScenarioRunner.class);

  private static final Map<Class<? extends TestCaseInstruction>, TestCaseInstructionHandler<?>>
      INSTRUCTION_HANDLERS = new HashMap<>();

  static {
    // register instruction handlers here
    registerHandler(new AssertDecisionInstructionHandler());
    registerHandler(new AssertElementInstanceInstructionHandler());
    registerHandler(new AssertElementInstancesInstructionHandler());
    registerHandler(new AssertProcessInstanceInstructionHandler());
    registerHandler(new AssertProcessInstanceMessageSubscriptionInstructionHandler());
    registerHandler(new AssertUserTaskInstructionHandler());
    registerHandler(new AssertVariablesInstructionHandler());
    registerHandler(new BroadcastSignalInstructionHandler());
    registerHandler(new CompleteJobInstructionHandler());
    registerHandler(new CompleteJobAdHocSubProcessInstructionHandler());
    registerHandler(new CompleteJobUserTaskListenerInstructionHandler());
    registerHandler(new CompleteUserTaskInstructionHandler());
    registerHandler(new CreateProcessInstanceInstructionHandler());
    registerHandler(new EvaluateConditionalStartEventInstructionHandler());
    registerHandler(new EvaluateDecisionInstructionHandler());
    registerHandler(new IncreaseTimeInstructionHandler());
    registerHandler(new MockChildProcessInstructionHandler());
    registerHandler(new MockDmnDecisionInstructionHandler());
    registerHandler(new MockJobWorkerCompleteJobInstructionHandler());
    registerHandler(new MockJobWorkerThrowBpmnErrorInstructionHandler());
    registerHandler(new PublishMessageInstructionHandler());
    registerHandler(new CorrelateMessageInstructionHandler());
    registerHandler(new ResolveIncidentInstructionHandler());
    registerHandler(new SetTimeInstructionHandler());
    registerHandler(new ThrowBpmnErrorFromJobInstructionHandler());
    registerHandler(new UpdateVariablesInstructionHandler());
  }

  private final CamundaProcessTestContext context;
  private final AssertionFacade assertionFacade;

  public CamundaTestScenarioRunner(final CamundaProcessTestContext context) {
    this(context, new TestScenarioAssertionFacade());
  }

  public CamundaTestScenarioRunner(
      final CamundaProcessTestContext context, final AssertionFacade assertionFacade) {
    this.context = context;
    this.assertionFacade = assertionFacade;
  }

  private static void registerHandler(
      final TestCaseInstructionHandler<? extends TestCaseInstruction> handler) {
    INSTRUCTION_HANDLERS.put(handler.getInstructionType(), handler);
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
    final TestCaseInstructionHandler instructionHandler = getInstructionHandler(instruction);

    try {
      //noinspection unchecked
      instructionHandler.execute(instruction, context, camundaClient, assertionFacade);

    } catch (final Exception e) {
      throw new TestScenarioRunException(
          String.format(
              "Failed to execute instruction '%s': %s", instruction.getType(), instruction),
          e);
    }
  }

  private static TestCaseInstructionHandler<?> getInstructionHandler(
      final TestCaseInstruction instruction) {
    final Class<?> instructionInterface = getInstructionInterface(instruction);
    return Optional.ofNullable(INSTRUCTION_HANDLERS.get(instructionInterface))
        .orElseThrow(
            () ->
                new RuntimeException(
                    "No handler found for instruction: " + instruction.getClass()));
  }

  private static Class<?> getInstructionInterface(final TestCaseInstruction instruction) {
    return Arrays.stream(instruction.getClass().getInterfaces())
        .filter(TestCaseInstruction.class::isAssignableFrom)
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Could not determine instruction interface of: " + instruction));
  }

  private static final class TestScenarioAssertionFacade implements AssertionFacade {

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
