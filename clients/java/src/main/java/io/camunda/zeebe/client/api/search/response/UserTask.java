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
package io.camunda.zeebe.client.api.search.response;

import java.util.List;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.response.UserTask}
 */
@Deprecated
public interface UserTask {

  Long getKey();

  /** State of the task */
  String getState();

  /** Assignee of the task */
  String getAssignee();

  /** Element ID */
  String getElementId();

  /** Instance key of the element */
  Long getElementInstanceKey();

  /** Candidate groups for the task */
  List<String> getCandidateGroup();

  /** Candidate users for the task */
  List<String> getCandidateUser();

  /** BPMN process id of the process associated with this task */
  String getBpmnProcessId();

  /** Key of the process definition */
  Long getProcessDefinitionKey();

  /** Key of the process instance */
  Long getProcessInstanceKey();

  /** Key of the form */
  Long getFormKey();

  /** Creation date of the task */
  String getCreationDate();

  /** Completion date of the task */
  String getCompletionDate();

  /** Follow-up date of the task */
  String getFollowUpDate();

  /** Due date of the task */
  String getDueDate();

  /** Tenant identifiers */
  String getTenantIds();

  /** External form reference */
  String getExternalFormReference();

  /** Version of the process definition */
  Integer getProcessDefinitionVersion();

  /** Custom headers associated with the task */
  Map<String, String> getCustomHeaders();

  /** Priority of the task */
  Integer getPriority();
}
