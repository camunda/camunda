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
package io.camunda.client.api.command;

import io.camunda.client.api.response.UpdateUserTaskResponse;
import java.util.List;

public interface UpdateUserTaskCommandStep1 extends FinalCommandStep<UpdateUserTaskResponse> {

  /**
   * Set the custom action to update the user task with.
   *
   * @param action the action value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 action(String action);

  /**
   * Set the due date to set in the user task. Use {@link #clearDueDate()} to remove the due date
   * from the task.
   *
   * @param dueDate the due date to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 dueDate(String dueDate);

  /**
   * Clear the due date in the user task.
   *
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 clearDueDate();

  /**
   * Set the follow-up date to set in the user task. Use {@link #clearFollowUpDate()} to remove the
   * follow-up date from the task.
   *
   * @param followUpDate the follow-up date to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 followUpDate(String followUpDate);

  /**
   * Clear the follow-up date in the user task.
   *
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 clearFollowUpDate();

  /**
   * Set the candidate groups to set in the user task. This replaces the candidate groups in the
   * task. Use {@link #clearCandidateGroups()} to remove the candidate groups from the task.
   *
   * @param candidateGroups the candidate groups to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 candidateGroups(List<String> candidateGroups);

  /**
   * Set the candidate groups to set in the user task. This replaces the candidate groups in the
   * task. Use {@link #clearCandidateGroups()} to remove the candidate groups from the task.
   *
   * @param candidateGroups the candidate groups to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 candidateGroups(String... candidateGroups);

  /**
   * Remove the candidate groups from the user task.
   *
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 clearCandidateGroups();

  /**
   * Set the candidate users to set in the user task. This replaces the candidate users in the task.
   * Use {@link #clearCandidateUsers()} to remove the candidate users from the task.
   *
   * @param candidateUsers the candidate users to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 candidateUsers(List<String> candidateUsers);

  /**
   * Set the candidate users to set in the user task. This replaces the candidate users in the task.
   * Use {@link #clearCandidateUsers()} to remove the candidate users from the task.
   *
   * @param candidateUsers the candidate users to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 candidateUsers(String... candidateUsers);

  /**
   * Remove the candidate users from the user task.
   *
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 clearCandidateUsers();

  /**
   * Set the priority of the User Task. The priority must be an integer between 0 and 100, default
   * 50.
   *
   * @param priority the priority to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdateUserTaskCommandStep1 priority(Integer priority);
}
