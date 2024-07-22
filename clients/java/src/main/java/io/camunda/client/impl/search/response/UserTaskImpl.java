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
package io.camunda.client.impl.search.response;

import io.camunda.client.protocol.rest.UserTaskItem;
import java.util.List;

public class UserTaskImpl {

  private final Long key;
  private final String taskState;
  private final String assignee;
  private final String taskDefinitionId;
  private final List<String> candidateGroup;
  private final List<String> candidateUser;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long formKey;
  private final String creationDate;
  private final String completionDate;
  private final String followUpDate;
  private final String dueDate;
  private final String tenantIds;
  private final String externalFormReference;

  public UserTaskImpl(final UserTaskItem item) {
    key = item.getUserTaskKey();
    taskState = item.getTaskState();
    assignee = item.getAssignee();
    taskDefinitionId = item.getElementId();
    candidateGroup = item.getCandidateGroup();
    candidateUser = item.getCandidateUser();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    formKey = item.getFormKey();
    creationDate = item.getCreationDate();
    completionDate = item.getCompletionDate();
    followUpDate = item.getFollowUpDate();
    dueDate = item.getDueDate();
    tenantIds = item.getTenantIds();
    externalFormReference = item.getExternalFormReference();
  }

  public Long getKey() {
    return key;
  }

  public String getTaskState() {
    return taskState;
  }

  public String getAssignee() {
    return assignee;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public List<String> getCandidateGroup() {
    return candidateGroup;
  }

  public List<String> getCandidateUser() {
    return candidateUser;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Long getFormKey() {
    return formKey;
  }

  public Long setFormKey() {
    return formKey;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public String getFollowUpDate() {
    return followUpDate;
  }

  public String getDueDate() {
    return dueDate;
  }

  public String getTenantIds() {
    return tenantIds;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }
}
