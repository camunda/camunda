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
import io.camunda.process.test.api.dsl.JobSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to complete a job. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCompleteJobInstruction.Builder.class)
public interface CompleteJobInstruction extends TestCaseInstruction {

  @Override
  default String getType() {
    return TestCaseInstructionType.COMPLETE_JOB;
  }

  /**
   * The selector to identify the job to complete.
   *
   * @return the job selector
   */
  JobSelector getJobSelector();

  /**
   * The variables to complete the job with. Defaults to an empty map.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();

  /**
   * Whether to complete the job with example data from the BPMN element. Defaults to false.
   *
   * <p>This property has precedence over {@link #getVariables()}. If example data is true, then
   * variables are ignored.
   *
   * @return true if example data should be used, false otherwise
   */
  @Value.Default
  default boolean getUseExampleData() {
    return false;
  }
}
