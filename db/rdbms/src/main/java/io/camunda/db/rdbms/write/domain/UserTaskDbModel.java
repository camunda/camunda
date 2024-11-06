/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.CustomHeaderSerializer;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class UserTaskDbModel {

  private Long key;
  private String flowNodeBpmnId; // elementId in UserTaskFilter
  private String processDefinitionId;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private String assignee;
  private UserTaskState state;
  private Long formKey;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long elementInstanceKey;
  private String tenantId;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private String externalFormReference;
  private Integer processDefinitionVersion;
  private String serializedCustomHeaders;
  private Map<String, String> customHeaders;
  private Integer priority;

  public UserTaskDbModel(final Long userTaskKey) {
    key = userTaskKey;
  }

  public UserTaskDbModel(
      final Long userTaskKey,
      final String flowNodeBpmnId,
      final String processDefinitionId,
      final OffsetDateTime creationTime,
      final OffsetDateTime completionTime,
      final String assignee,
      final UserTaskState state,
      final Long formKey,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final Long elementInstanceKey,
      final String tenantId,
      final OffsetDateTime dueDate,
      final OffsetDateTime followUpDate,
      final String externalFormReference,
      final Integer processDefinitionVersion,
      final Map<String, String> customHeaders,
      final Integer priority) {
    key = userTaskKey;
    this.flowNodeBpmnId = flowNodeBpmnId;
    this.processDefinitionId = processDefinitionId;
    this.creationTime = creationTime;
    this.completionTime = completionTime;
    this.assignee = assignee;
    this.state = state;
    this.formKey = formKey;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
    this.tenantId = tenantId;
    this.dueDate = dueDate;
    this.followUpDate = followUpDate;
    this.externalFormReference = externalFormReference;
    this.processDefinitionVersion = processDefinitionVersion;
    this.customHeaders = customHeaders;
    serializedCustomHeaders = CustomHeaderSerializer.serialize(customHeaders);
    this.priority = priority;
  }

  // Methods without get/set prefix

  public Long key() {
    return key;
  }

  public void key(final Long key) {
    this.key = key;
  }

  public String flowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public void flowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public void processDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public OffsetDateTime creationTime() {
    return creationTime;
  }

  public void creationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
  }

  public OffsetDateTime completionTime() {
    return completionTime;
  }

  public void completionTime(final OffsetDateTime completionTime) {
    this.completionTime = completionTime;
  }

  public String assignee() {
    return assignee;
  }

  public void assignee(final String assignee) {
    this.assignee = assignee;
  }

  public UserTaskState state() {
    return state;
  }

  public void state(final UserTaskState state) {
    this.state = state;
  }

  public Long formKey() {
    return formKey;
  }

  public void formKey(final Long formKey) {
    this.formKey = formKey;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long elementInstanceKey() {
    return elementInstanceKey;
  }

  public void elementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OffsetDateTime dueDate() {
    return dueDate;
  }

  public void dueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public OffsetDateTime followUpDate() {
    return followUpDate;
  }

  public void followUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
  }

  public List<String> candidateGroups() {
    return candidateGroups;
  }

  public void candidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public List<String> candidateUsers() {
    return candidateUsers;
  }

  public void candidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  public String externalFormReference() {
    return externalFormReference;
  }

  public void externalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
  }

  public Integer processDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void processDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String serializedCustomHeaders() {
    return serializedCustomHeaders;
  }

  public void serializedCustomHeaders(final String serializedCustomHeaders) {
    this.serializedCustomHeaders = serializedCustomHeaders;
    customHeaders = CustomHeaderSerializer.deserialize(serializedCustomHeaders);
  }

  public Map<String, String> customHeaders() {
    return customHeaders;
  }

  public void customHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public Integer priority() {
    return priority;
  }

  public void priority(final Integer priority) {
    this.priority = priority;
  }

  public Builder toBuilder() {
    return new Builder()
        .key(key)
        .flowNodeBpmnId(flowNodeBpmnId)
        .processDefinitionId(processDefinitionId)
        .creationTime(creationTime)
        .completionTime(completionTime)
        .assignee(assignee)
        .state(state)
        .formKey(formKey)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .tenantId(tenantId)
        .dueDate(dueDate)
        .followUpDate(followUpDate)
        .candidateGroups(candidateGroups)
        .candidateUsers(candidateUsers)
        .externalFormReference(externalFormReference)
        .processDefinitionVersion(processDefinitionVersion)
        .customHeaders(customHeaders)
        .priority(priority);
  }

  public static class Builder implements ObjectBuilder<UserTaskDbModel> {

    private Long key;
    private String flowNodeBpmnId;
    private String processDefinitionId;
    private OffsetDateTime creationTime;
    private OffsetDateTime completionTime;
    private String assignee;
    private UserTaskDbModel.UserTaskState state;
    private Long formKey;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long elementInstanceKey;
    private String tenantId;
    private OffsetDateTime dueDate;
    private OffsetDateTime followUpDate;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private String externalFormReference;
    private Integer processDefinitionVersion;
    private Map<String, String> customHeaders;
    private Integer priority;

    // Public constructor to initialize the builder
    public Builder() {}

    public static UserTaskDbModel of(
        final Function<UserTaskDbModel.Builder, ObjectBuilder<UserTaskDbModel>> fn) {
      return fn.apply(new UserTaskDbModel.Builder()).build();
    }

    // Builder methods for each field
    public Builder key(final Long key) {
      this.key = key;
      return this;
    }

    public Builder flowNodeBpmnId(final String flowNodeBpmnId) {
      this.flowNodeBpmnId = flowNodeBpmnId;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder creationTime(final OffsetDateTime creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    public Builder completionTime(final OffsetDateTime completionTime) {
      this.completionTime = completionTime;
      return this;
    }

    public Builder assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    public Builder state(final UserTaskDbModel.UserTaskState state) {
      this.state = state;
      return this;
    }

    public Builder formKey(final Long formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder dueDate(final OffsetDateTime dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public Builder followUpDate(final OffsetDateTime followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    public Builder candidateGroups(final List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    public Builder candidateUsers(final List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    public Builder externalFormReference(final String externalFormReference) {
      this.externalFormReference = externalFormReference;
      return this;
    }

    public Builder processDefinitionVersion(final int processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    public Builder customHeaders(final Map<String, String> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    public Builder priority(final int priority) {
      this.priority = priority;
      return this;
    }

    // Build method to create the record
    @Override
    public UserTaskDbModel build() {
      final var model =
          new UserTaskDbModel(
              key,
              flowNodeBpmnId,
              processDefinitionId,
              creationTime,
              completionTime,
              assignee,
              state,
              formKey,
              processDefinitionKey,
              processInstanceKey,
              elementInstanceKey,
              tenantId,
              dueDate,
              followUpDate,
              externalFormReference,
              processDefinitionVersion,
              customHeaders,
              priority);

      model.candidateUsers(candidateUsers);
      model.candidateGroups(candidateGroups);

      return model;
    }
  }

  public enum UserTaskState {
    CREATED,
    COMPLETED,
    CANCELED,
    FAILED
  }
}
