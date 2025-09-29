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
package io.camunda.process.test.api.assertions;

import java.time.OffsetDateTime;
import java.util.List;

/** The assertion object to verify a user task. */
public interface UserTaskAssert {
  /**
   * Verifies that the user task is created. The verification fails if the task is completed,
   * cancelled, failed or not created.
   *
   * <p>The assertion waits until the user task is created.
   *
   * @return the assertion object
   */
  UserTaskAssert isCreated();

  /**
   * Verifies that the user task is completed. The verification fails if the task is active,
   * cancelled, failed or not created.
   *
   * <p>The assertion waits until the user task is completed.
   *
   * @return the assertion object
   */
  UserTaskAssert isCompleted();

  /**
   * Verifies that the user task is cancelled. The verification fails if the task is active,
   * completed, failed or not created.
   *
   * <p>The assertion waits until the user task is canceled.
   *
   * @return the assertion object
   */
  UserTaskAssert isCanceled();

  /**
   * Verifies that the user task is failed. The verification fails if the task is active, completed,
   * cancelled or not created.
   *
   * <p>The assertion waits until the user task is failed.
   *
   * @return the assertion object
   */
  UserTaskAssert isFailed();

  /**
   * Verifies that the user task has the expected assignee.
   *
   * @param assignee person assigned to the user task
   * @return the assertion object
   */
  UserTaskAssert hasAssignee(final String assignee);

  /**
   * Verifies that the user task has the given priority
   *
   * @param priority the priority of the user task
   * @return the assertion object
   */
  UserTaskAssert hasPriority(final int priority);

  /**
   * Verifies that the user task has the expected element id
   *
   * @param elementId the id of the user task
   * @return the assertion object
   */
  UserTaskAssert hasElementId(final String elementId);

  /**
   * Verifies that the user task has the expected name
   *
   * @param name the name of the user task
   * @return the assertion object
   */
  UserTaskAssert hasName(final String name);

  /**
   * Verifies that the user task has the expected process instance key.
   *
   * @param processInstanceKey the process instance key
   * @return the assertion object
   */
  UserTaskAssert hasProcessInstanceKey(final long processInstanceKey);

  /**
   * Verifies that the user task has the expected due date.
   *
   * @param dueDate the due date of the user task in ISO-8601 format
   * @return the assertion object
   */
  UserTaskAssert hasDueDate(final OffsetDateTime dueDate);

  /**
   * Verifies that the user task has the expected completion date.
   *
   * @param completionDate the completion date of the user task in ISO-8601 format
   * @return the assertion object
   */
  UserTaskAssert hasCompletionDate(final OffsetDateTime completionDate);

  /**
   * Verifies that the user task has the expected follow-up date.
   *
   * @param followUpDate the follow-up date of the user task in ISO-8601 format
   * @return the assertion object
   */
  UserTaskAssert hasFollowUpDate(final OffsetDateTime followUpDate);

  /**
   * Verifies that the user task has the expected creation date.
   *
   * @param creationDate the creation date of the user task in ISO-8601 format
   * @return the assertion object
   */
  UserTaskAssert hasCreationDate(final OffsetDateTime creationDate);

  /**
   * Verifies that the user task contains the expected candidate group.
   *
   * @param candidateGroup the candidate group to check for
   * @return the assertion object
   */
  UserTaskAssert hasCandidateGroup(final String candidateGroup);

  /**
   * Verifies that the user task contains all the expected candidate groups.
   *
   * @param candidateGroups the list of candidate groups to check for
   * @return the assertion object
   */
  UserTaskAssert hasCandidateGroups(final List<String> candidateGroups);
}
