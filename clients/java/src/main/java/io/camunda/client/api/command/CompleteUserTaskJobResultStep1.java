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

public interface CompleteUserTaskJobResultStep1 extends CompleteJobResult {
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
  CompleteUserTaskJobResultStep1 deny(final boolean isDenied);

  /**
   * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it. For example,
   * a user task listener can deny the completion of a task by setting this flag to true. In this
   * example, the completion of a task is represented by a job that the worker can complete as
   * denied. As a result, the completion request is rejected and the task remains active. Defaults
   * to {@code false}. This method also allows setting the reason for denying the job. Default to an
   * empty string.
   *
   * @param isDenied indicates if the worker has denied the reason for the job
   * @param deniedReason indicates the reason why the worker denied the job
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 deny(final boolean isDenied, final String deniedReason);

  /**
   * Indicates the reason why the worker denied the job. For example, a user task listener can deny
   * the completion of a task by setting the deny flag to true and specifying the reason to deny.
   * Defaults to an empty string.
   *
   * @param deniedReason indicates the reason why the worker denied the job
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 deniedReason(final String deniedReason);

  /**
   * Applies corrections to the user task attributes.
   *
   * <p>This method allows the worker to modify key attributes of the user task (such as {@code
   * assignee}, {@code candidateGroups}, and so on).
   *
   * @param corrections the corrections to apply to the user task.
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correct(final JobResultCorrections corrections);

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
  CompleteUserTaskJobResultStep1 correct(final UnaryOperator<JobResultCorrections> corrections);

  /**
   * Correct the assignee of the task.
   *
   * @param assignee assignee of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctAssignee(final String assignee);

  /**
   * Correct the due date of the task.
   *
   * @param dueDate due date of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctDueDate(final String dueDate);

  /**
   * Correct the follow up date of the task.
   *
   * @param followUpDate follow up date of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctFollowUpDate(final String followUpDate);

  /**
   * Correct the candidate groups of the task.
   *
   * @param candidateGroups candidate groups of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctCandidateGroups(final List<String> candidateGroups);

  /**
   * Correct the candidate users of the task.
   *
   * @param candidateUsers candidate users of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctCandidateUsers(final List<String> candidateUsers);

  /**
   * Correct the priority of the task.
   *
   * @param priority priority of the task
   * @return this job result
   */
  CompleteUserTaskJobResultStep1 correctPriority(final Integer priority);
}
