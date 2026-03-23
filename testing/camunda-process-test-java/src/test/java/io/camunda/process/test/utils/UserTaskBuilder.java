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
package io.camunda.process.test.utils;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserTaskBuilder implements UserTask {

  private static final OffsetDateTime CREATION_DATE = OffsetDateTime.parse("2025-03-20T13:23:00Z");

  private Long userTaskKey;
  private String name;
  private UserTaskState state;
  private String assignee;
  private String elementId;
  private Long elementInstanceKey;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private String bpmnProcessId;
  private String processName;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private Long formKey;
  private OffsetDateTime creationDate;
  private OffsetDateTime completionDate;
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;
  private String tenantId;
  private String externalFormReference;
  private Integer processDefinitionVersion;
  private Map<String, String> customHeaders;
  private Integer priority;
  private Set<String> tags;

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

  public UserTaskBuilder setTags(final Set<String> tags) {
    this.tags = tags;
    return this;
  }

  public UserTaskBuilder setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public UserTaskBuilder setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public UserTaskBuilder setProcessDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public UserTaskBuilder setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public UserTaskBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public UserTaskBuilder setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public UserTaskBuilder setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public UserTaskBuilder setCompletionDate(final OffsetDateTime completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public UserTaskBuilder setCreationDate(final OffsetDateTime creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public UserTaskBuilder setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public UserTaskBuilder setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public UserTaskBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public UserTaskBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public UserTaskBuilder setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public UserTaskBuilder setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public UserTaskBuilder setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public UserTaskBuilder setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public UserTaskBuilder setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public UserTaskBuilder setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public UserTaskBuilder setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public UserTaskBuilder setState(final UserTaskState state) {
    this.state = state;
    return this;
  }

  public UserTaskBuilder setName(final String name) {
    this.name = name;
    return this;
  }

  public UserTaskBuilder setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public UserTask build() {
    return this;
  }

  public static UserTaskBuilder newCreatedUserTask(final long userTaskKey) {
    return new UserTaskBuilder()
        .setUserTaskKey(userTaskKey)
        .setState(UserTaskState.CREATED)
        .setCreationDate(CREATION_DATE);
  }
}
