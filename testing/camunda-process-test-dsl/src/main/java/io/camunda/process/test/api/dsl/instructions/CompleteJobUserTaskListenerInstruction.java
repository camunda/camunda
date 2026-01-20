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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to complete a job of a user task listener. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCompleteJobUserTaskListenerInstruction.Builder.class)
public interface CompleteJobUserTaskListenerInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.COMPLETE_JOB_USER_TASK_LISTENER;
  }

  /**
   * The selector to identify the job to complete.
   *
   * @return the job selector
   */
  JobSelector getJobSelector();

  /**
   * The variables to complete the job with. Optional.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();

  /**
   * Whether the worker denies the work. Defaults to false.
   *
   * @return true if the worker denies the work, false otherwise
   */
  @Value.Default
  default boolean getDenied() {
    return false;
  }

  /**
   * The reason for denying the job. Optional.
   *
   * @return the denied reason
   */
  Optional<String> getDeniedReason();

  /**
   * The corrections to apply to the user task. Optional.
   *
   * @return the corrections
   */
  Optional<Corrections> getCorrections();

  /** Corrections to apply to the user task attributes. */
  @Value.Immutable
  @JsonDeserialize(builder = ImmutableCorrections.Builder.class)
  interface Corrections {

    /**
     * The assignee of the task.
     *
     * @return the assignee
     */
    Optional<String> getAssignee();

    /**
     * The due date of the task.
     *
     * @return the due date
     */
    Optional<String> getDueDate();

    /**
     * The follow up date of the task.
     *
     * @return the follow up date
     */
    Optional<String> getFollowUpDate();

    /**
     * The candidate users of the task.
     *
     * @return the candidate users
     */
    List<String> getCandidateUsers();

    /**
     * The candidate groups of the task.
     *
     * @return the candidate groups
     */
    List<String> getCandidateGroups();

    /**
     * The priority of the task.
     *
     * @return the priority
     */
    Optional<Integer> getPriority();
  }
}
