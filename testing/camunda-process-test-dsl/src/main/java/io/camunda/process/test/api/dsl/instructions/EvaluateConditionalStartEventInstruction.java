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
import java.util.Map;
import org.immutables.value.Value;

/** An instruction to evaluate conditional start events. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableEvaluateConditionalStartEventInstruction.Builder.class)
public interface EvaluateConditionalStartEventInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.EVALUATE_CONDITIONAL_START_EVENT;
  }

  /**
   * The variables to evaluate the conditional start events with.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();
}
