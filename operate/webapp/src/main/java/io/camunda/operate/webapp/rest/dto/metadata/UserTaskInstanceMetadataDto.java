/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class UserTaskInstanceMetadataDto extends ServiceTaskInstanceMetadataDto
    implements FlowNodeInstanceMetadata {
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private Long formKey;
  private String action;
  private List<String> changedAttributes;
  private String assignee;
  private Long userTaskKey;
  private Map<String, Object> variables = Map.of();
  private List<String> candidateGroups = List.of();
  private List<String> candidateUsers = List.of();
  private String tenantId;
  private String externalReference;

  public UserTaskInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UserTaskInstanceMetadataDto setExternalReference(final String externalReference) {
    this.externalReference = externalReference;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UserTaskInstanceMetadataDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public UserTaskInstanceMetadataDto setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public UserTaskInstanceMetadataDto setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public UserTaskInstanceMetadataDto setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getAction() {
    return action;
  }

  public UserTaskInstanceMetadataDto setAction(final String action) {
    this.action = action;
    return this;
  }

  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public UserTaskInstanceMetadataDto setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
    return this;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public UserTaskInstanceMetadataDto setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public UserTaskInstanceMetadataDto setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public UserTaskInstanceMetadataDto setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public UserTaskInstanceMetadataDto setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public UserTaskInstanceMetadataDto setVariables(final Map<String, Object> variables) {
    this.variables = variables;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        dueDate,
        followUpDate,
        formKey,
        action,
        changedAttributes,
        assignee,
        userTaskKey,
        variables,
        candidateGroups,
        candidateUsers,
        tenantId,
        externalReference);
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
    final UserTaskInstanceMetadataDto that = (UserTaskInstanceMetadataDto) o;
    return Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(action, that.action)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(userTaskKey, that.userTaskKey)
        && Objects.equals(variables, that.variables)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(externalReference, that.externalReference);
  }

  @Override
  public String toString() {
    return "UserTaskInstanceMetadataDto{"
        + "dueDate="
        + dueDate
        + ", followUpDate="
        + followUpDate
        + ", formKey="
        + formKey
        + ", action='"
        + action
        + '\''
        + ", changedAttributes="
        + changedAttributes
        + ", assignee='"
        + assignee
        + '\''
        + ", userTaskKey="
        + userTaskKey
        + ", variables="
        + variables
        + ", candidateGroups="
        + candidateGroups
        + ", candidateUsers="
        + candidateUsers
        + ", tenantId='"
        + tenantId
        + '\''
        + ", externalReference='"
        + externalReference
        + '\''
        + "} "
        + super.toString();
  }
}
