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
import io.camunda.process.test.api.testCases.ElementSelector;
import io.camunda.process.test.api.testCases.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import org.immutables.value.Value;

/**
 * An instruction to fire a held timer immediately, without waiting for its due date.
 *
 * <p>The selected element is the timer catch event of a process instance created with {@code
 * holdTimers}, whose timers never fire on their own.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableTriggerTimerInstruction.Builder.class)
public interface TriggerTimerInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.TRIGGER_TIMER;
  }

  /**
   * The selector to identify the process instance that owns the held timer.
   *
   * @return the process instance selector
   */
  ProcessInstanceSelector getProcessInstanceSelector();

  /**
   * The selector to identify the timer catch event. The element must be selected by elementId: the
   * engine addresses a held timer by the element id of its catch event, which for a boundary timer
   * is the boundary event's own id rather than that of the activity it is attached to.
   *
   * @return the element selector
   */
  ElementSelector getElementSelector();
}
