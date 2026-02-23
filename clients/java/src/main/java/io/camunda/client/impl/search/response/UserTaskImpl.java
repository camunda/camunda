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

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.UserTaskResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserTaskImpl implements UserTask {

  private final Long userTaskKey;
  private final String name;
  private final UserTaskState state;
  private final String assignee;
  private final String elementId;
  private final Long elementInstanceKey;
  private final List<String> candidateGroup;
  private final List<String> candidateUser;
  private final String bpmnProcessId;
  private final String processName;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final Long formKey;
  private final OffsetDateTime creationDate;
  private final OffsetDateTime completionDate;
  private final OffsetDateTime followUpDate;
  private final OffsetDateTime dueDate;
  private final String tenantId;
  private final String externalFormReference;
  private final Integer processDefinitionVersion;
  private final Map<String, String> customHeaders;
  private final Integer priority;
  private final Set<String> tags;

  public UserTaskImpl(final UserTaskResult item) {
    userTaskKey = ParseUtil.parseLongOrNull(item.getUserTaskKey());
    name = item.getName();
    state = EnumUtil.convert(item.getState(), UserTaskState.class);
    assignee = item.getAssignee();
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    candidateGroup = item.getCandidateGroups();
    candidateUser = item.getCandidateUsers();
    bpmnProcessId = item.getProcessDefinitionId();
    processName = item.getProcessName();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
    formKey = ParseUtil.parseLongOrNull(item.getFormKey());
    creationDate = ParseUtil.parseOffsetDateTimeOrNull(item.getCreationDate());
    completionDate = ParseUtil.parseOffsetDateTimeOrNull(item.getCompletionDate());
    followUpDate = ParseUtil.parseOffsetDateTimeOrNull(item.getFollowUpDate());
    dueDate = ParseUtil.parseOffsetDateTimeOrNull(item.getDueDate());
    tenantId = item.getTenantId();
    externalFormReference = item.getExternalFormReference();
    processDefinitionVersion = item.getProcessDefinitionVersion();
    customHeaders = item.getCustomHeaders();
    priority = item.getPriority();
    tags = item.getTags();
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
  public UserTaskState getState() {
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
  public String getProcessName() {
    return processName;
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
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public Long getFormKey() {
    return formKey;
  }

  @Override
  public OffsetDateTime getCreationDate() {
    return creationDate;
  }

  @Override
  public OffsetDateTime getCompletionDate() {
    return completionDate;
  }

  @Override
  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  @Override
  public OffsetDateTime getDueDate() {
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

  @Override
  public Set<String> getTags() {
    return tags;
  }
}
