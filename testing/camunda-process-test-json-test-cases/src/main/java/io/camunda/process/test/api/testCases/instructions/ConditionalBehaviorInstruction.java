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
package io.camunda.process.test.api.testCases.instructions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * An instruction to register a conditional behavior that reacts to process state changes.
 *
 * <p>The behavior watches the given {@link #getConditions() conditions} (each an {@code ASSERT_*}
 * instruction) on a background scheduler. The conditions form a conjunction: the behavior fires
 * only when every assertion succeeds. When that happens, the next action in {@link #getActions()
 * actions} is executed. Actions are consumed in order: the first match fires the first action, the
 * second match fires the second, and the last action repeats indefinitely once preceding ones are
 * exhausted.
 *
 * <p>Conditional behaviors should be registered before any {@code CREATE_PROCESS_INSTANCE}
 * instruction in the same test case; behaviors registered later may miss state changes that
 * happened before registration.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableConditionalBehaviorInstruction.Builder.class)
public interface ConditionalBehaviorInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.CONDITIONAL_BEHAVIOR;
  }

  /**
   * An optional descriptive name for this behavior. Used in log messages and diagnostics.
   *
   * @return the name or empty if not set
   */
  Optional<String> getName();

  /**
   * The conditions to watch. Must contain at least one {@code ASSERT_*} instruction. The conditions
   * form a conjunction: the behavior fires only when every assertion succeeds.
   *
   * @return the ordered list of condition instructions
   */
  List<TestCaseInstruction> getConditions();

  /**
   * The actions to execute when the conditions are met. Must contain at least one action. Actions
   * are consumed in order: the first match fires the first action, the second match fires the
   * second, and the last action repeats indefinitely.
   *
   * @return the ordered list of action instructions
   */
  List<TestCaseInstruction> getActions();
}
