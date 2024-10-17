/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class UserTaskEntity extends OperateZeebeEntity<UserTaskEntity> {

  private Long userTaskKey;
  private String assignee;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private Long formKey;
  private String elementId;
  private Long elementInstanceKey;
  private String bpmnProcessId;
  private Long processDefinitionKey;
  private Integer processDefinitionVersion;
  private Long processInstanceKey;
  private String variables;
  private String externalReference;
  private String action;
  private List<String> changedAttributes;
  private String tenantId = DEFAULT_TENANT_ID;
  private Integer priority;

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public UserTaskEntity setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public UserTaskEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public UserTaskEntity setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public UserTaskEntity setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public UserTaskEntity setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public UserTaskEntity setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public UserTaskEntity setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public UserTaskEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public UserTaskEntity setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public UserTaskEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public UserTaskEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public UserTaskEntity setProcessDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public UserTaskEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getVariables() {
    return variables;
  }

  public UserTaskEntity setVariables(final String variables) {
    this.variables = variables;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UserTaskEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UserTaskEntity setExternalReference(final String externalReference) {
    this.externalReference = externalReference;
    return this;
  }

  public String getAction() {
    return action;
  }

  public UserTaskEntity setAction(final String action) {
    this.action = action;
    return this;
  }

  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public UserTaskEntity setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public UserTaskEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        userTaskKey,
        assignee,
        candidateGroups,
        candidateUsers,
        dueDate,
        followUpDate,
        formKey,
        elementId,
        elementInstanceKey,
        bpmnProcessId,
        processDefinitionKey,
        processDefinitionVersion,
        processInstanceKey,
        variables,
        externalReference,
        action,
        changedAttributes,
        tenantId,
        priority);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final UserTaskEntity that = (UserTaskEntity) o;
    return Objects.equals(userTaskKey, that.userTaskKey)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(elementInstanceKey, that.elementInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionVersion, that.processDefinitionVersion)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(variables, that.variables)
        && Objects.equals(externalReference, that.externalReference)
        && Objects.equals(action, that.action)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(priority, that.priority);
  }
}
