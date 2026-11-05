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
package io.camunda.process.test.api.testCases.instructions.createProcessInstance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.testCases.TestCaseJobReservationToken;
import java.util.List;
import org.immutables.value.Value;

/**
 * A runtime instruction that reserves the process instance's jobs of the listed job types, so that
 * no job worker is served them. Jobs of any other type run for real.
 */
@Value.Immutable
@JsonDeserialize(
    builder = ImmutableCreateProcessInstanceReserveJobsRuntimeInstruction.Builder.class)
public interface CreateProcessInstanceReserveJobsRuntimeInstruction
    extends CreateProcessInstanceRuntimeInstruction {

  @Value.Default
  @Override
  default String getType() {
    return CreateProcessInstanceRuntimeInstructionType.RESERVE_JOBS;
  }

  /**
   * The job types whose jobs are reserved. A job is reserved when its type is in this list;
   * selection is by job type, so every job of a listed type is reserved.
   *
   * @return the job types to reserve
   */
  List<String> getJobTypes();

  /**
   * The token that reserves the listed jobs. Use {@link TestCaseJobReservationToken#PLACEHOLDER}:
   * the runner substitutes a fresh token for it on each run.
   *
   * @return the job reservation token
   */
  String getJobReservationToken();
}
