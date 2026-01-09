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

/** An instruction to throw a BPMN error from a job. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableThrowBpmnErrorFromJobInstruction.Builder.class)
public interface ThrowBpmnErrorFromJobInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.THROW_BPMN_ERROR_FROM_JOB;
  }

  /**
   * The selector to identify the job to throw the error from.
   *
   * @return the job selector
   */
  JobSelector getJobSelector();

  /**
   * The error code to throw.
   *
   * @return the error code
   */
  String getErrorCode();

  /**
   * The error message to throw. Optional.
   *
   * @return the error message or empty if not set
   */
  Optional<String> getErrorMessage();

  /**
   * The variables to set when throwing the error. Defaults to an empty map.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();
}
