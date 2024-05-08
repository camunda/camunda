/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v850.record.value;

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

  @Override
  public long getUserTaskKey() {
    return userTaskKey;
  }

  public void setUserTaskKey(long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(String candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public String getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(String candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(String followUpDate) {
    this.followUpDate = followUpDate;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes;
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

  public void setChangedAttributes(List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  public void setFormKey(long formKey) {
    this.formKey = formKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return candidateGroupsList;
  }

  public void setCandidateGroupsList(List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return candidateUsersList;
  }

  public void setCandidateUsersList(List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  @Override
  public boolean equals(Object o) {
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
        && Objects.equals(changedAttributes, that.changedAttributes);
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
        changedAttributes);
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
        + ", changedAttributes="
        + changedAttributes
        + '}';
  }
}
