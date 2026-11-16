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
import io.camunda.process.test.api.testCases.TestCaseReservationToken;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to mock a job worker who throws BPMN errors. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableMockJobWorkerThrowBpmnErrorInstruction.Builder.class)
public interface MockJobWorkerThrowBpmnErrorInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.MOCK_JOB_WORKER_THROW_BPMN_ERROR;
  }

  /**
   * The job type to mock. This should match the `zeebeJobType` in the BPMN model. Exactly one of
   * {@code jobType} or {@code calledProcessId} is set.
   *
   * @return the job type or empty when mocking a call activity
   */
  Optional<String> getJobType();

  /**
   * The called-process id whose stubbed call activities to stand in for, instead of a job type.
   * When set, the mock drains the stub jobs of every call activity calling this process to throw a
   * BPMN error — driving the call activity's error boundary event. Stub jobs are never reserved, so
   * {@link #getReservationToken()} is ignored. Exactly one of {@code jobType} or {@code
   * calledProcessId} is set.
   *
   * @return the called-process id or empty when mocking an ordinary job type
   */
  Optional<String> getCalledProcessId();

  /**
   * The reservation token the jobs of this type were reserved with. Use {@link
   * TestCaseReservationToken#PLACEHOLDER}: the runner substitutes the token of the current run for
   * it. Optional; required only to mock a reserved job type, so the mock worker can activate its
   * reserved jobs on a remote cluster.
   *
   * @return the job reservation token or empty if not set
   */
  Optional<String> getReservationToken();

  /**
   * The error code to throw. This should match the error code in an error catch event.
   *
   * @return the error code
   */
  String getErrorCode();

  /**
   * The error message to include when throwing the error. Optional.
   *
   * @return the error message or empty if no error message is set
   */
  Optional<String> getErrorMessage();

  /**
   * The variables to include when throwing the error. Optional.
   *
   * @return the variables or an empty map if no variables are set
   */
  Map<String, Object> getVariables();
}
