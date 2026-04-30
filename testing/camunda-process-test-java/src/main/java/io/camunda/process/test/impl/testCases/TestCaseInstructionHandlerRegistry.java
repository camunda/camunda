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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.impl.testCases.instructions.AssertDecisionInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertElementInstanceInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertElementInstancesInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertProcessInstanceInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertProcessInstanceMessageSubscriptionInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertUserTaskInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.AssertVariablesInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.BroadcastSignalInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CompleteJobAdHocSubProcessInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CompleteJobInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CompleteJobUserTaskListenerInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CompleteUserTaskInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.ConditionalBehaviorInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CorrelateMessageInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.CreateProcessInstanceInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.EvaluateConditionalStartEventInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.EvaluateDecisionInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.IncreaseTimeInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.MockChildProcessInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.MockDmnDecisionInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.MockJobWorkerCompleteJobInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.MockJobWorkerThrowBpmnErrorInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.PublishMessageInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.ResolveIncidentInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.SetTimeInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.ThrowBpmnErrorFromJobInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.UpdateVariablesInstructionHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@link TestCaseInstructionHandler}s by their declared instruction interface and
 * dispatches instructions to them.
 */
public class TestCaseInstructionHandlerRegistry {

  private final Map<Class<? extends TestCaseInstruction>, TestCaseInstructionHandler<?>> handlers =
      new HashMap<>();

  public TestCaseInstructionHandlerRegistry() {}

  /** Registers the given handler under the instruction interface it declares. */
  public <T extends TestCaseInstruction> void register(
      final TestCaseInstructionHandler<T> handler) {
    handlers.put(handler.getInstructionType(), handler);
  }

  /**
   * Looks up the handler for the given instruction.
   *
   * @throws RuntimeException if no handler is registered for the instruction's interface
   */
  public TestCaseInstructionHandler<?> getHandler(final TestCaseInstruction instruction) {
    final Class<?> instructionInterface = getInstructionInterface(instruction);
    return Optional.ofNullable(handlers.get(instructionInterface))
        .orElseThrow(
            () ->
                new RuntimeException(
                    "No handler found for instruction: " + instruction.getClass()));
  }

  /** Dispatches the given instruction to its registered handler. */
  public void dispatch(
      final TestCaseInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {
    @SuppressWarnings({"rawtypes", "unchecked"})
    final TestCaseInstructionHandler instructionHandler = getHandler(instruction);
    //noinspection unchecked
    instructionHandler.execute(instruction, context, camundaClient, assertionFacade);
  }

  /**
   * Builds a registry pre-populated with the handlers shipped by Camunda Process Test, including
   * the conditional behavior handler that recursively dispatches via the same registry.
   */
  public static TestCaseInstructionHandlerRegistry defaultRegistry() {
    final TestCaseInstructionHandlerRegistry registry = new TestCaseInstructionHandlerRegistry();
    registry.register(new AssertDecisionInstructionHandler());
    registry.register(new AssertElementInstanceInstructionHandler());
    registry.register(new AssertElementInstancesInstructionHandler());
    registry.register(new AssertProcessInstanceInstructionHandler());
    registry.register(new AssertProcessInstanceMessageSubscriptionInstructionHandler());
    registry.register(new AssertUserTaskInstructionHandler());
    registry.register(new AssertVariablesInstructionHandler());
    registry.register(new BroadcastSignalInstructionHandler());
    registry.register(new CompleteJobInstructionHandler());
    registry.register(new CompleteJobAdHocSubProcessInstructionHandler());
    registry.register(new CompleteJobUserTaskListenerInstructionHandler());
    registry.register(new CompleteUserTaskInstructionHandler());
    registry.register(new ConditionalBehaviorInstructionHandler(registry));
    registry.register(new CreateProcessInstanceInstructionHandler());
    registry.register(new EvaluateConditionalStartEventInstructionHandler());
    registry.register(new EvaluateDecisionInstructionHandler());
    registry.register(new IncreaseTimeInstructionHandler());
    registry.register(new MockChildProcessInstructionHandler());
    registry.register(new MockDmnDecisionInstructionHandler());
    registry.register(new MockJobWorkerCompleteJobInstructionHandler());
    registry.register(new MockJobWorkerThrowBpmnErrorInstructionHandler());
    registry.register(new PublishMessageInstructionHandler());
    registry.register(new CorrelateMessageInstructionHandler());
    registry.register(new ResolveIncidentInstructionHandler());
    registry.register(new SetTimeInstructionHandler());
    registry.register(new ThrowBpmnErrorFromJobInstructionHandler());
    registry.register(new UpdateVariablesInstructionHandler());
    return registry;
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
}
