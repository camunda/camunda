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

/** An instruction to mock a job worker who completes jobs. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableMockJobWorkerCompleteJobInstruction.Builder.class)
public interface MockJobWorkerCompleteJobInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.MOCK_JOB_WORKER_COMPLETE_JOB;
  }

  /**
   * The job type to mock. This should match the `zeebeJobType` in the BPMN model.
   *
   * @return the job type
   */
  String getJobType();

  /**
   * The variables to complete the job with. Optional.
   *
   * @return the variables or an empty map if no variables are set
   */
  Map<String, Object> getVariables();

  /**
   * Whether to use example data from the BPMN element. Optional, default is false. If set to true,
   * the variables property is ignored.
   *
   * @return true if example data should be used, false otherwise
   */
  @Value.Default
  default boolean getWithExampleData() {
    return false;
  }
}
