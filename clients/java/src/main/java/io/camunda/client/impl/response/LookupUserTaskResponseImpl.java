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
package io.camunda.client.impl.response;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.LookupUserTaskResponse;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.UserTaskLookupResult;
import io.camunda.client.protocol.rest.UserTaskLookupStateEnum;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LookupUserTaskResponseImpl implements LookupUserTaskResponse {

  private final JsonMapper jsonMapper;
  private final Long userTaskKey;
  private final UserTaskState state;
  private final String assignee;
  private final String elementId;
  private final Long elementInstanceKey;
  private final List<String> candidateGroups;
  private final List<String> candidateUsers;
  private final String bpmnProcessId;
  private final Long processDefinitionKey;
  private final Integer processDefinitionVersion;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final String businessId;
  private final Long formKey;
  private final OffsetDateTime creationDate;
  private final OffsetDateTime followUpDate;
  private final OffsetDateTime dueDate;
  private final String tenantId;
  private final String externalFormReference;
  private final Map<String, String> customHeaders;
  private final Integer priority;
  private final Set<String> tags;
  private final String variables;

  private Map<String, Object> variablesAsMap;

  public LookupUserTaskResponseImpl(
      final JsonMapper jsonMapper, final UserTaskLookupResult response) {
    this.jsonMapper = jsonMapper;
    userTaskKey = ParseUtil.parseLongOrNull(response.getUserTaskKey());
    state = toUserTaskState(response.getState());
    assignee = response.getAssignee();
    elementId = response.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(response.getElementInstanceKey());
    candidateGroups = response.getCandidateGroups();
    candidateUsers = response.getCandidateUsers();
    bpmnProcessId = response.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(response.getProcessDefinitionKey());
    processDefinitionVersion = response.getProcessDefinitionVersion();
    processInstanceKey = ParseUtil.parseLongOrNull(response.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(response.getRootProcessInstanceKey());
    businessId = response.getBusinessId();
    formKey = ParseUtil.parseLongOrNull(response.getFormKey());
    creationDate = ParseUtil.parseOffsetDateTimeOrNull(response.getCreationDate());
    followUpDate = ParseUtil.parseOffsetDateTimeOrNull(response.getFollowUpDate());
    dueDate = ParseUtil.parseOffsetDateTimeOrNull(response.getDueDate());
    tenantId = response.getTenantId();
    externalFormReference = response.getExternalFormReference();
    customHeaders = response.getCustomHeaders();
    priority = response.getPriority();
    tags = response.getTags();
    variables = jsonMapper.toJson(response.getVariables());
  }

  @Override
  public Long getUserTaskKey() {
    return userTaskKey;
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
    return candidateGroups;
  }

  @Override
  public List<String> getCandidateUsers() {
    return candidateUsers;
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
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getBusinessId() {
    return businessId;
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

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    if (variablesAsMap == null) {
      variablesAsMap = jsonMapper.fromJsonAsMap(variables);
    }
    return variablesAsMap;
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
  }

  @Override
  public Object getVariable(final String name) {
    final Map<String, Object> variablesAsMap = getVariablesAsMap();
    if (!variablesAsMap.containsKey(name)) {
      throw new ClientException(String.format("The variable %s is not available", name));
    }
    return variablesAsMap.get(name);
  }

  private static UserTaskState toUserTaskState(final UserTaskLookupStateEnum state) {
    // TODO(#68590): remove once the lookup endpoint reports a claim in progress as ASSIGNING
    if (state == UserTaskLookupStateEnum.CLAIMING) {
      return UserTaskState.ASSIGNING;
    }
    return EnumUtil.convert(state, UserTaskState.class);
  }
}
