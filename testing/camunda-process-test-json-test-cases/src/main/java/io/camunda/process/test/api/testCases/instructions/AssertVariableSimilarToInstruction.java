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
import java.util.Optional;
import org.immutables.value.Value;

/**
 * An instruction to assert that a process variable is semantically similar to an expected value.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAssertVariableSimilarToInstruction.Builder.class)
public interface AssertVariableSimilarToInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.ASSERT_VARIABLE_SIMILAR_TO;
  }

  /**
   * The selector to identify the process instance.
   *
   * @return the process instance selector
   */
  ProcessInstanceSelector getProcessInstanceSelector();

  /**
   * The selector to identify the element for local variable scope. Optional — when absent the
   * variable is looked up as a global process-instance variable.
   *
   * @return the element selector or empty for global scope
   */
  Optional<ElementSelector> getElementSelector();

  /**
   * The name of the variable to assert.
   *
   * @return the variable name
   */
  String getVariableName();

  /**
   * The expected value the variable should be semantically similar to.
   *
   * @return the expected value
   */
  String getExpectedValue();

  /**
   * The minimum similarity threshold (0.0–1.0) for the assertion to pass. Optional — when absent
   * the configured default threshold is used.
   *
   * @return the threshold or empty to use the default
   */
  Optional<Double> getThreshold();
}
