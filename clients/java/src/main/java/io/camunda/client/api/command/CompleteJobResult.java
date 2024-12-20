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

import java.util.List;

public class CompleteJobResult {

  private boolean isDenied;
  private JobResultCorrections corrections;

  public CompleteJobResult() {
    corrections = new JobResultCorrections();
  }

  /**
   * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it. For example,
   * a Task Listener can deny the completion of a task by setting this flag to true. In this
   * example, the completion of a task is represented by a job that the worker can complete as
   * denied. As a result, the completion request is rejected and the task remains active. Defaults
   * to false.
   *
   * @param isDenied true if the work must be denied, false otherwise
   * @return this job result
   */
  public CompleteJobResult deny(final boolean isDenied) {
    this.isDenied = isDenied;
    return this;
  }

  /**
   * Correct the task.
   *
   * @param corrections corrections to apply
   * @return this job result
   */
  public CompleteJobResult correct(final JobResultCorrections corrections) {
    if (corrections == null) {
      this.corrections = new JobResultCorrections();
    } else {
      this.corrections = corrections;
    }
    return this;
  }

  /**
   * Correct the assignee of the task.
   *
   * @param assignee assignee of the task
   * @return this job result
   */
  public CompleteJobResult correctAssignee(final String assignee) {
    corrections.assignee(assignee);
    return this;
  }

  /**
   * Correct the due date of the task.
   *
   * @param dueDate due date of the task
   * @return this job result
   */
  public CompleteJobResult correctDueDate(final String dueDate) {
    corrections.dueDate(dueDate);
    return this;
  }

  /**
   * Correct the follow up date of the task.
   *
   * @param followUpDate follow up date of the task
   * @return this job result
   */
  public CompleteJobResult correctFollowUpDate(final String followUpDate) {
    corrections.followUpDate(followUpDate);
    return this;
  }

  /**
   * Correct the candidate groups of the task.
   *
   * @param candidateGroups candidate groups of the task
   * @return this job result
   */
  public CompleteJobResult correctCandidateGroups(final List<String> candidateGroups) {
    corrections.candidateGroups(candidateGroups);
    return this;
  }

  /**
   * Correct the candidate users of the task.
   *
   * @param candidateUsers candidate users of the task
   * @return this job result
   */
  public CompleteJobResult correctCandidateUsers(final List<String> candidateUsers) {
    corrections.candidateUsers(candidateUsers);
    return this;
  }

  /**
   * Correct the priority of the task.
   *
   * @param priority priority of the task
   * @return this job result
   */
  public CompleteJobResult correctPriority(final int priority) {
    corrections.priority(priority);
    return this;
  }

  public boolean isDenied() {
    return isDenied;
  }

  public JobResultCorrections getCorrections() {
    return corrections;
  }
}
