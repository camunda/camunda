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
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import io.camunda.process.test.api.testCases.VariablesTableRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
   * The variables to complete the job with. Optional. Mutually exclusive with {@link
   * #getVariablesExpression()} and a non-empty {@link #getVariablesTable()}. The legacy combination
   * with {@link #getUseExampleData()} still ignores these variables.
   *
   * @return the variables or an empty map if no variables are set
   */
  Map<String, Object> getVariables();

  /**
   * A FEEL expression evaluated with the job's variables as context. Must evaluate to a JSON
   * object; its entries become the job's output variables. A leading {@code =} is optional.
   * Optional. Mutually exclusive with {@link #getVariables()}, a non-empty {@link
   * #getVariablesTable()} and {@link #getUseExampleData()}.
   *
   * @return the FEEL expression or empty if not set
   */
  Optional<String> getVariablesExpression();

  /**
   * A list of {@code when}/{@code variables} rows. Rows are evaluated in order; the first row whose
   * {@code when} map is a subset match of the job's variables supplies the output variables. An
   * empty {@code when} matches every job. If no row matches, the job completes without variables.
   * Optional. Mutually exclusive with {@link #getVariables()}, {@link #getVariablesExpression()}
   * and {@link #getUseExampleData()}.
   *
   * @return the variables table rows, or an empty list if not set
   */
  List<VariablesTableRow> getVariablesTable();

  /**
   * Whether to use example data from the BPMN element. Optional, default is false. If set to true,
   * the variables property is ignored. Mutually exclusive with {@link #getVariablesExpression()}
   * and a non-empty {@link #getVariablesTable()}.
   *
   * @return true if example data should be used, false otherwise
   */
  @Value.Default
  default boolean getUseExampleData() {
    return false;
  }
}
