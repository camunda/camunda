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

import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.UserTaskResult;
import java.util.List;
import java.util.Map;

public class UserTaskImpl implements UserTask {

  private final Long userTaskKey;
  private final String name;
  private final io.camunda.client.api.search.enums.UserTaskResult.State state;
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
  private final String tenantId;
  private final String externalFormReference;
  private final Integer processDefinitionVersion;
  private final Map<String, String> customHeaders;
  private final Integer priority;

  public UserTaskImpl(final UserTaskResult item) {
    userTaskKey = ParseUtil.parseLongOrNull(item.getUserTaskKey());
    name = item.getName();
    state =
        EnumUtil.convert(
            item.getState(), io.camunda.client.api.search.enums.UserTaskResult.State.class);
    assignee = item.getAssignee();
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    candidateGroup = item.getCandidateGroups();
    candidateUser = item.getCandidateUsers();
    bpmnProcessId = item.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    formKey = ParseUtil.parseLongOrNull(item.getFormKey());
    creationDate = item.getCreationDate();
    completionDate = item.getCompletionDate();
    followUpDate = item.getFollowUpDate();
    dueDate = item.getDueDate();
    tenantId = item.getTenantId();
    externalFormReference = item.getExternalFormReference();
    processDefinitionVersion = item.getProcessDefinitionVersion();
    customHeaders = item.getCustomHeaders();
    priority = item.getPriority();
  }

  @Override
  public Long getUserTaskKey() {
    return userTaskKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public io.camunda.client.api.search.enums.UserTaskResult.State getState() {
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
  public List<String> getCandidateGroups() {
    return candidateGroup;
  }

  @Override
  public List<String> getCandidateUsers() {
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
  public String getCreationDate() {
    return creationDate;
  }

  @Override
  public String getCompletionDate() {
    return completionDate;
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
  public String getTenantId() {
    return tenantId;
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
  public Integer getPriority() {
    return priority;
  }
}
