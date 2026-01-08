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
import java.util.Collections;
import java.util.Map;
import org.immutables.value.Value;

/** An instruction to mock a DMN decision. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableMockDmnDecisionInstruction.Builder.class)
public interface MockDmnDecisionInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.MOCK_DMN_DECISION;
  }

  /**
   * The decision definition ID to mock.
   *
   * @return the decision definition ID
   */
  String getDecisionDefinitionId();

  /**
   * The variables to set as the decision output. Optional.
   *
   * @return the variables or an empty map if no variables are set
   */
  @Value.Default
  default Map<String, Object> getVariables() {
    return Collections.emptyMap();
  }
}
