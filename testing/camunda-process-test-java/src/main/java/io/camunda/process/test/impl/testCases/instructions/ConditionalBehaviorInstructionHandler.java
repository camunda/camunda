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
import io.camunda.process.test.api.testCases.instructions.ConditionalBehaviorInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandlerRegistry;
import java.util.List;

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

    if (instruction.getActions().isEmpty()) {
      throw new IllegalArgumentException(
          "CONDITIONAL_BEHAVIOR requires at least one action, but the actions list is empty.");
    }

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
}
