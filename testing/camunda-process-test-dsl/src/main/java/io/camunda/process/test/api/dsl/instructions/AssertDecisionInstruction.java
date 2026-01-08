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
import io.camunda.process.test.api.dsl.DecisionSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to assert the evaluation of a decision. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAssertDecisionInstruction.Builder.class)
public interface AssertDecisionInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.ASSERT_DECISION;
  }

  /**
   * The selector to identify the decision.
   *
   * @return the decision selector
   */
  DecisionSelector getDecisionSelector();

  /**
   * The expected output of the decision. Optional.
   *
   * @return the expected output or empty if not asserted
   */
  Optional<Object> getOutput();

  /**
   * The expected matched rule indexes. Optional.
   *
   * @return the expected matched rule indexes or empty list if not asserted
   */
  List<Integer> getMatchedRules();

  /**
   * The expected not matched rule indexes. Optional.
   *
   * @return the expected not matched rule indexes or empty list if not asserted
   */
  List<Integer> getNotMatchedRules();

  /**
   * Whether to assert that no rules were matched. Defaults to {@code false}.
   *
   * @return true if asserting no rules matched, false otherwise
   */
  @Value.Default
  default boolean getNoMatchedRules() {
    return false;
  }
}
