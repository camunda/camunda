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
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import io.camunda.process.test.api.dsl.UserTaskSelector;
import java.util.Map;
import org.immutables.value.Value;

/** An instruction to complete a user task. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCompleteUserTaskInstruction.Builder.class)
public interface CompleteUserTaskInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.COMPLETE_USER_TASK;
  }

  /**
   * The selector to identify the user task to complete.
   *
   * @return the user task selector
   */
  UserTaskSelector getUserTaskSelector();

  /**
   * The variables to set when completing the user task. Ignored if useExampleData is true.
   *
   * @return the variables or an empty map if not set
   */
  Map<String, Object> getVariables();

  /**
   * Whether to complete the user task with example data from the BPMN element. If true, the
   * variables property is ignored.
   *
   * @return true if example data should be used, false otherwise
   */
  @Value.Default
  default boolean getUseExampleData() {
    return false;
  }
}
