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
import java.util.function.UnaryOperator;

public class CompleteJobResult {

  private boolean isDenied;
  private String deniedReason;
  private JobResultCorrections corrections;

  public CompleteJobResult() {
    corrections = new JobResultCorrections();
  }

  /**
   * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it. For example,
   * a user task listener can deny the completion of a task by setting this flag to true. In this
   * example, the completion of a task is represented by a job that the worker can complete as
   * denied. As a result, the completion request is rejected and the task remains active. Defaults
   * to {@code false}.
   *
   * @param isDenied indicates if the worker has denied the reason for the job
   * @return this job result
   */
  public CompleteJobResult deny(final boolean isDenied) {
    this.isDenied = isDenied;
    return this;
  }

  /**
   * Indicates the reason why the worker denied the job. For example, a user task listener can deny
   * the completion of a task by setting the deny flag to true and specifying the reason to deny.
   * Defaults to an empty string.
   *
   * @param deniedReason indicates the reason why the worker denied the job
   * @return this job result
   */
  public CompleteJobResult deniedReason(final String deniedReason) {
    this.deniedReason = deniedReason;
    return this;
  }

  /**
   * Applies corrections to the user task attributes.
   *
   * <p>This method allows the worker to modify key attributes of the user task (such as {@code
   * assignee}, {@code candidateGroups}, and so on).
   *
   * @param corrections the corrections to apply to the user task.
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
   * Dynamically applies corrections to the user task attributes using a lambda expression.
   *
   * <p>This method is a functional alternative to {@link #correct(JobResultCorrections)}. It allows
   * the worker to modify key user task attributes (such as {@code assignee}, {@code dueDate},
   * {@code priority}, and so on) directly via a lambda expression. The lambda receives the current
   * {@link JobResultCorrections} instance, which can be updated as needed. If no corrections have
   * been set yet, a default {@link JobResultCorrections} instance is provided.
   *
   * @param corrections a lambda expression to modify the {@link JobResultCorrections}.
   * @return this job result
   */
  public CompleteJobResult correct(final UnaryOperator<JobResultCorrections> corrections) {
    return correct(corrections.apply(this.corrections));
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
  public CompleteJobResult correctPriority(final Integer priority) {
    corrections.priority(priority);
    return this;
  }

  public boolean isDenied() {
    return isDenied;
  }

  public String getDeniedReason() {
    return deniedReason;
  }

  public JobResultCorrections getCorrections() {
    return corrections;
  }
}
