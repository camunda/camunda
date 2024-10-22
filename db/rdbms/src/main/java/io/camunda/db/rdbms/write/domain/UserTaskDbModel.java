/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskDbModel {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskDbModel.class);

  private final Long key;
  private final String flowNodeBpmnId; // elementId in UserTaskFilter
  private final String processDefinitionId;
  private final OffsetDateTime creationTime;
  private final OffsetDateTime completionTime;
  private final String assignee;
  private final UserTaskState state;
  private final Long formKey;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long elementInstanceKey;
  private final String tenantId;
  private final OffsetDateTime dueDate;
  private final OffsetDateTime followUpDate;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private final String externalFormReference;
  private final Integer processDefinitionVersion;
  private String serializedCustomHeaders;
  private Map<String, String> customHeaders;
  private Integer priority;

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
      final String customHeaders,
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
    serializedCustomHeaders = customHeaders;
    this.priority = priority;
  }

  // Getters and Setters for modifiable fields

  public List<String> candidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public List<String> candidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  public String serializedCustomHeaders() {
    return serializedCustomHeaders;
  }

  public void setSerializedCustomHeaders(final String serializedCustomHeaders) {
    this.serializedCustomHeaders = serializedCustomHeaders;
  }

  public Map<String, String> customHeaders() {
    return customHeaders;
  }

  public void setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public Integer priority() {
    return priority;
  }

  public void setPriority(final Integer priority) {
    this.priority = priority;
  }

  // Getters for non-modifiable fields

  public Long key() {
    return key;
  }

  public String flowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public OffsetDateTime creationTime() {
    return creationTime;
  }

  public OffsetDateTime completionTime() {
    return completionTime;
  }

  public String assignee() {
    return assignee;
  }

  public UserTaskState state() {
    return state;
  }

  public Long formKey() {
    return formKey;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public Long elementInstanceKey() {
    return elementInstanceKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public OffsetDateTime dueDate() {
    return dueDate;
  }

  public OffsetDateTime followUpDate() {
    return followUpDate;
  }

  public String externalFormReference() {
    return externalFormReference;
  }

  public Integer processDefinitionVersion() {
    return processDefinitionVersion;
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
        .serializedCustomHeaders(serializedCustomHeaders)
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
    private String serializedCustomHeaders;
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
      final ObjectMapper mapper = new ObjectMapper();
      try {
        serializedCustomHeaders = mapper.writeValueAsString(customHeaders);
      } catch (final JsonProcessingException e) {
        LOG.warn("Failed to serialize custom headers!", e);
      }
      return this;
    }

    public Builder serializedCustomHeaders(final String serializedCustomHeaders) {
      this.serializedCustomHeaders = serializedCustomHeaders;
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
              serializedCustomHeaders,
              priority);

      model.setCandidateUsers(candidateUsers);
      model.setCandidateGroups(candidateGroups);

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
