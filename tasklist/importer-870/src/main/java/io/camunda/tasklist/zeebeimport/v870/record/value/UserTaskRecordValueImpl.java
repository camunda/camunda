/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record.value;

import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UserTaskRecordValueImpl implements UserTaskRecordValue {
  private long userTaskKey;
  private String assignee;
  private String candidateGroups;
  private List<String> candidateGroupsList;
  private String candidateUsers;
  private List<String> candidateUsersList;
  private String dueDate;

  private String followUpDate;

  private long formKey;

  private String elementId;

  private long elementInstanceKey;

  private String bpmnProcessId;

  private int processDefinitionVersion;

  private long processDefinitionKey;

  private Map<String, Object> variables;

  private String tenantId;

  private long processInstanceKey;

  private List<String> changedAttributes;
  private String action;
  private String externalFormReference;
  private Map<String, String> customHeaders;
  private long creationTimestamp;
  private int priority;

  @Override
  public long getUserTaskKey() {
    return userTaskKey;
  }

  public void setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return candidateGroupsList;
  }

  public void setCandidateGroupsList(final List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return candidateUsersList;
  }

  public void setCandidateUsersList(final List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(final String dueDate) {
    this.dueDate = dueDate;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  public void setFormKey(final long formKey) {
    this.formKey = formKey;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public void setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  @Override
  public String getAction() {
    return action;
  }

  @Override
  public String getExternalFormReference() {
    return externalFormReference;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public long getCreationTimestamp() {
    return creationTimestamp;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  public String getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final String candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public String getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final String candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
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
        processDefinitionVersion,
        processDefinitionKey,
        variables,
        tenantId,
        processInstanceKey,
        changedAttributes,
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
    final UserTaskRecordValueImpl that = (UserTaskRecordValueImpl) o;
    return userTaskKey == that.userTaskKey
        && formKey == that.formKey
        && elementInstanceKey == that.elementInstanceKey
        && processDefinitionVersion == that.processDefinitionVersion
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(variables, that.variables)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(priority, that.priority);
  }

  @Override
  public String toString() {
    return "UserTaskRecordValueImpl{"
        + "userTaskKey="
        + userTaskKey
        + ", assignee='"
        + assignee
        + '\''
        + ", candidateGroups='"
        + candidateGroups
        + '\''
        + ", candidateUsers='"
        + candidateUsers
        + '\''
        + ", dueDate='"
        + dueDate
        + '\''
        + ", followUpDate='"
        + followUpDate
        + '\''
        + ", formKey="
        + formKey
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", variables="
        + variables
        + ", tenantId='"
        + tenantId
        + '\''
        + ", processInstanceKey="
        + processInstanceKey
        + ", priority="
        + priority
        + ", changedAttributes="
        + changedAttributes
        + '}';
  }
}
