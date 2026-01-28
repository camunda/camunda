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
package io.camunda.process.test.api.dsl.instructions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.dsl.ElementSelector;
import io.camunda.process.test.api.dsl.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import io.camunda.process.test.api.dsl.instructions.assertElementInstance.ElementInstanceState;
import org.immutables.value.Value;

/** An instruction to assert the state of an element instance. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAssertElementInstanceInstruction.Builder.class)
public interface AssertElementInstanceInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.ASSERT_ELEMENT_INSTANCE;
  }

  /**
   * The selector to identify the process instance.
   *
   * @return the process instance selector
   */
  ProcessInstanceSelector getProcessInstanceSelector();

  /**
   * The selector to identify the element.
   *
   * @return the element selector
   */
  ElementSelector getElementSelector();

  /**
   * The expected state of the element instance.
   *
   * @return the expected state
   */
  ElementInstanceState getState();

  /**
   * The expected amount of element instances in the given state. Default is 1.
   *
   * @return the expected amount
   */
  @Value.Default
  default int getAmount() {
    return 1;
  }
}
