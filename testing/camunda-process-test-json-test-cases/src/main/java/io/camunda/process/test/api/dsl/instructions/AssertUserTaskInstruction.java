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
import io.camunda.process.test.api.dsl.UserTaskSelector;
import io.camunda.process.test.api.dsl.instructions.assertUserTask.UserTaskState;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to assert the state of a user task. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAssertUserTaskInstruction.Builder.class)
public interface AssertUserTaskInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.ASSERT_USER_TASK;
  }

  /**
   * The selector to identify the user task.
   *
   * @return the user task selector
   */
  UserTaskSelector getUserTaskSelector();

  /**
   * The expected state of the user task. Optional.
   *
   * @return the expected state or empty if not asserted
   */
  Optional<UserTaskState> getState();

  /**
   * The expected assignee of the user task. Optional.
   *
   * @return the expected assignee or empty if not asserted
   */
  Optional<String> getAssignee();

  /**
   * The expected candidate groups of the user task. Optional.
   *
   * @return the expected candidate groups or empty list if not asserted
   */
  List<String> getCandidateGroups();

  /**
   * The expected priority of the user task. Optional.
   *
   * @return the expected priority or empty if not asserted
   */
  Optional<Integer> getPriority();

  /**
   * The expected element ID of the user task. Optional.
   *
   * @return the expected element ID or empty if not asserted
   */
  Optional<String> getElementId();

  /**
   * The expected name of the user task. Optional.
   *
   * @return the expected name or empty if not asserted
   */
  Optional<String> getName();

  /**
   * The expected due date of the user task in ISO-8601 format. Optional.
   *
   * @return the expected due date or empty if not asserted
   */
  Optional<String> getDueDate();

  /**
   * The expected follow-up date of the user task in ISO-8601 format. Optional.
   *
   * @return the expected follow-up date or empty if not asserted
   */
  Optional<String> getFollowUpDate();
}
