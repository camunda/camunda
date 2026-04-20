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

/** An instruction to assert a process variable using an LLM judge evaluation. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAssertVariableSatisfiesJudgeInstruction.Builder.class)
public interface AssertVariableSatisfiesJudgeInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.ASSERT_VARIABLE_SATISFIES_JUDGE;
  }

  /**
   * The selector to identify the process instance.
   *
   * @return the process instance selector
   */
  ProcessInstanceSelector getProcessInstanceSelector();

  /**
   * The name of the variable to evaluate.
   *
   * @return the variable name
   */
  String getVariableName();

  /**
   * The semantic expectation for the variable value.
   *
   * @return the expectation text
   */
  String getExpectation();

  /**
   * The selector to identify the element for local variables. Optional.
   *
   * @return the element selector or empty if asserting global variables
   */
  Optional<ElementSelector> getElementSelector();

  /**
   * The score threshold above which the judge assertion passes. Must be between {@code 0.0} and
   * {@code 1.0}; higher values are stricter. Overrides the preconfigured threshold. Optional;
   * defaults to the preconfigured {@code JudgeConfig} threshold ({@code 0.5} unless otherwise
   * configured) when omitted.
   *
   * @return the threshold or empty if using the preconfigured value
   */
  Optional<Double> getThreshold();

  /**
   * A custom prompt for the judge evaluation. Overrides the preconfigured custom prompt. Optional.
   *
   * @return the custom prompt or empty if using the preconfigured value
   */
  Optional<String> getCustomPrompt();
}
