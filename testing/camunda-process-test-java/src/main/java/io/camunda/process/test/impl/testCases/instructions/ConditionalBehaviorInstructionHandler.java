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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.behavior.BehaviorCondition;
import io.camunda.process.test.api.behavior.ConditionalBehaviorBuilder;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import io.camunda.process.test.api.testCases.instructions.ConditionalBehaviorInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandlerRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handler that registers a {@link ConditionalBehaviorInstruction} on the test context. The
 * conditions become a single {@link BehaviorCondition} that recursively dispatches each nested
 * {@code ASSERT_*} instruction through the registry as a conjunction; each action becomes a {@link
 * Runnable} that dispatches its nested instruction.
 *
 * <p>The condition lambda and action runnables are executed by the conditional behavior engine on
 * background threads. The registered instruction handlers are stateless and the {@link
 * CamundaProcessTestContext}/{@link CamundaClient} are documented as thread-safe, so concurrent
 * invocation is supported.
 */
public class ConditionalBehaviorInstructionHandler
    implements TestCaseInstructionHandler<ConditionalBehaviorInstruction> {

  private static final Set<String> ALLOWED_CONDITION_TYPES =
      sortedSetOf(
          TestCaseInstructionType.ASSERT_DECISION,
          TestCaseInstructionType.ASSERT_ELEMENT_INSTANCE,
          TestCaseInstructionType.ASSERT_ELEMENT_INSTANCES,
          TestCaseInstructionType.ASSERT_PROCESS_INSTANCE,
          TestCaseInstructionType.ASSERT_PROCESS_INSTANCE_MESSAGE_SUBSCRIPTION,
          TestCaseInstructionType.ASSERT_USER_TASK,
          TestCaseInstructionType.ASSERT_VARIABLES);

  private static final Set<String> ALLOWED_ACTION_TYPES =
      sortedSetOf(
          TestCaseInstructionType.BROADCAST_SIGNAL,
          TestCaseInstructionType.COMPLETE_JOB,
          TestCaseInstructionType.COMPLETE_JOB_AD_HOC_SUB_PROCESS,
          TestCaseInstructionType.COMPLETE_JOB_USER_TASK_LISTENER,
          TestCaseInstructionType.COMPLETE_USER_TASK,
          TestCaseInstructionType.CORRELATE_MESSAGE,
          TestCaseInstructionType.PUBLISH_MESSAGE,
          TestCaseInstructionType.RESOLVE_INCIDENT,
          TestCaseInstructionType.THROW_BPMN_ERROR_FROM_JOB,
          TestCaseInstructionType.UPDATE_VARIABLES);

  private final TestCaseInstructionHandlerRegistry registry;

  public ConditionalBehaviorInstructionHandler(final TestCaseInstructionHandlerRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void execute(
      final ConditionalBehaviorInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    if (instruction.getConditions().isEmpty()) {
      throw new IllegalArgumentException(
          "CONDITIONAL_BEHAVIOR requires at least one condition, but the conditions list is"
              + " empty.");
    }
    instruction.getConditions().forEach(ConditionalBehaviorInstructionHandler::validateCondition);

    if (instruction.getActions().isEmpty()) {
      throw new IllegalArgumentException(
          "CONDITIONAL_BEHAVIOR requires at least one action, but the actions list is empty.");
    }
    instruction.getActions().forEach(ConditionalBehaviorInstructionHandler::validateAction);

    final List<TestCaseInstruction> conditionInstructions = instruction.getConditions();
    final BehaviorCondition condition =
        () -> {
          for (final TestCaseInstruction conditionInstruction : conditionInstructions) {
            registry.dispatch(conditionInstruction, context, camundaClient, assertionFacade);
          }
        };

    final ConditionalBehaviorBuilder builder = context.when(condition);
    instruction.getName().ifPresent(builder::as);

    for (final TestCaseInstruction action : instruction.getActions()) {
      builder.then(() -> registry.dispatch(action, context, camundaClient, assertionFacade));
    }
  }

  @Override
  public Class<ConditionalBehaviorInstruction> getInstructionType() {
    return ConditionalBehaviorInstruction.class;
  }

  private static void validateCondition(final TestCaseInstruction condition) {
    if (!ALLOWED_CONDITION_TYPES.contains(condition.getType())) {
      throw new IllegalArgumentException(
          String.format(
              "CONDITIONAL_BEHAVIOR condition type must be one of %s, but was: %s",
              ALLOWED_CONDITION_TYPES, condition.getType()));
    }
  }

  private static void validateAction(final TestCaseInstruction action) {
    if (!ALLOWED_ACTION_TYPES.contains(action.getType())) {
      throw new IllegalArgumentException(
          String.format(
              "CONDITIONAL_BEHAVIOR action type must be one of %s, but was: %s",
              ALLOWED_ACTION_TYPES, action.getType()));
    }
  }

  private static Set<String> sortedSetOf(final String... values) {
    return Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(values)));
  }
}
