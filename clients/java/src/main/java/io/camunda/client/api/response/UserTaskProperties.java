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
package io.camunda.client.api.response;

import java.util.List;
import java.time.OffsetDateTime;

/** Represents the properties of a user task associated with a job. */
public interface UserTaskProperties {

  /**
   * @return the action performed on the user task (e.g., "claim", "update", "complete").
   */
  String getAction();

  /**
   * @return the user assigned to the task.
   */
  String getAssignee();

  /**
   * @return the list of candidate groups for the user task.
   */
  List<String> getCandidateGroups();

  /**
   * @return the list of candidate users for the user task.
   */
  List<String> getCandidateUsers();

  /**
   * @return the list of attributes that were changed in the user task.
   */
  List<String> getChangedAttributes();

  /**
   * @return the due date of the user task in ISO 8601 format.
   */
  OffsetDateTime getDueDate();

  /**
   * @return the follow-up date of the user task in ISO 8601 format.
   */
  OffsetDateTime getFollowUpDate();

  /**
   * @return the key of the form associated with the user task.
   */
  Long getFormKey();

  /**
   * @return the priority of the user task (0-100).
   */
  Integer getPriority();

  /**
   * @return the unique key identifying the user task.
   */
  Long getUserTaskKey();
}
