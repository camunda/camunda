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
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to create or update process instance variables. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableUpdateVariablesInstruction.Builder.class)
public interface UpdateVariablesInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.UPDATE_VARIABLES;
  }

  /**
   * The selector to identify the process instance.
   *
   * @return the process instance selector
   */
  ProcessInstanceSelector getProcessInstanceSelector();

  /**
   * The variables to create or update.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();

  /**
   * The selector to identify the element for local variables. Optional.
   *
   * @return the element selector or empty if not set
   */
  Optional<ElementSelector> getElementSelector();
}
