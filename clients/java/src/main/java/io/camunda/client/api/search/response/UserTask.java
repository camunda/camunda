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
package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.UserTaskState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserTask {

  Long getUserTaskKey();

  /** Name of the task */
  String getName();

  /** State of the task */
  UserTaskState getState();

  /** Assignee of the task */
  String getAssignee();

  /** Element ID */
  String getElementId();

  /** Instance key of the element */
  Long getElementInstanceKey();

  /** Candidate groups for the task */
  List<String> getCandidateGroups();

  /** Candidate users for the task */
  List<String> getCandidateUsers();

  /** BPMN process id of the process associated with this task */
  String getBpmnProcessId();

  /** Name of the process definition */
  String getProcessName();

  /** Key of the process definition */
  Long getProcessDefinitionKey();

  /** Key of the process instance */
  Long getProcessInstanceKey();

  /**
   * Returns the key of the root process instance. The root process instance is the top-level
   * ancestor in the process instance hierarchy.
   *
   * <p><strong>Note:</strong> This field is {@code null} for process instance hierarchies created
   * before version 8.9.
   *
   * @return the root process instance key, or {@code null} for data created before version 8.9
   */
  Long getRootProcessInstanceKey();

  /** Key of the form */
  Long getFormKey();

  /** Creation date of the task */
  OffsetDateTime getCreationDate();

  /** Completion date of the task */
  OffsetDateTime getCompletionDate();

  /** Follow-up date of the task */
  OffsetDateTime getFollowUpDate();

  /** Due date of the task */
  OffsetDateTime getDueDate();

  /** Tenant identifiers */
  String getTenantId();

  /** External form reference */
  String getExternalFormReference();

  /** Version of the process definition */
  Integer getProcessDefinitionVersion();

  /** Custom headers associated with the task */
  Map<String, String> getCustomHeaders();

  /** Priority of the task */
  Integer getPriority();

  /** Tags associated with the task */
  Set<String> getTags();
}
