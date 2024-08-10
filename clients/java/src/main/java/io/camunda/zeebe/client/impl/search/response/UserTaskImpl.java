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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.protocol.rest.UserTaskItem;
import java.util.List;
import java.util.Map;

public class UserTaskImpl implements UserTask {

  private final Long key;
  private final String state;
  private final String assignee;
  private final String elementId;
  private final Long elementInstanceKey;
  private final List<String> candidateGroup;
  private final List<String> candidateUser;
  private final String bpmnProcessId;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long formKey;
  private final String creationDate;
  private final String completionDate;
  private final String followUpDate;
  private final String dueDate;
  private final String tenantIds;
  private final String externalFormReference;
  private final Integer processDefinitionVersion;
  private final Map<String, String> customHeaders;

  public UserTaskImpl(final UserTaskItem item) {
    key = item.getKey();
    state = item.getState();
    assignee = item.getAssignee();
    elementId = item.getElementId();
    elementInstanceKey = item.getElementInstanceKey();
    candidateGroup = item.getCandidateGroup();
    candidateUser = item.getCandidateUser();
    bpmnProcessId = item.getBpmnProcessId();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    formKey = item.getFormKey();
    creationDate = item.getCreationDate();
    completionDate = item.getCompletionDate();
    followUpDate = item.getFollowUpDate();
    dueDate = item.getDueDate();
    tenantIds = item.getTenantIds();
    externalFormReference = item.getExternalFormReference();
    processDefinitionVersion = item.getProcessDefinitionVersion();
    customHeaders = item.getCustomHeaders();
  }

  @Override
  public Long getKey() {
    return key;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public List<String> getCandidateGroup() {
    return candidateGroup;
  }

  @Override
  public List<String> getCandidateUser() {
    return candidateUser;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getFormKey() {
    return formKey;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  @Override
  public String getTenantIds() {
    return tenantIds;
  }

  @Override
  public String getExternalFormReference() {
    return externalFormReference;
  }

  @Override
  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getCreationDate() {
    return creationDate;
  }

  @Override
  public String getCompletionDate() {
    return completionDate;
  }
}
