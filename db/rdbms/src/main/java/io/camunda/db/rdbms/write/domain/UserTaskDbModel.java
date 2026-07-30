/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.MapSerializer;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public record UserTaskDbModel(
    Long userTaskKey,
    String elementId,
    String name,
    String processDefinitionId,
    OffsetDateTime creationDate,
    OffsetDateTime completionDate,
    String assignee,
    UserTaskState state,
    Long formKey,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    String businessId,
    Long elementInstanceKey,
    String tenantId,
    OffsetDateTime dueDate,
    OffsetDateTime followUpDate,
    List<String> candidateGroups,
    List<String> candidateUsers,
    String externalFormReference,
    Integer processDefinitionVersion,
    String serializedCustomHeaders,
    Integer priority,
    Set<String> tags,
    int partitionId)
    implements Copyable<UserTaskDbModel> {

  public UserTaskDbModel {
    // Must stay mutable: MyBatis appends to these via <collection> after construction.
    candidateGroups = Objects.requireNonNullElse(candidateGroups, new ArrayList<>());
    candidateUsers = Objects.requireNonNullElse(candidateUsers, new ArrayList<>());
    tags = Objects.requireNonNullElse(tags, new HashSet<>());
  }

  // Matches searchResultMap's <constructor>, which omits candidateGroups/candidateUsers/tags --
  // populated separately via the sibling <collection> elements -- and partitionId, which the
  // search query never selects (matching the pre-record behavior of always defaulting to 0 for
  // search-hydrated instances).
  public UserTaskDbModel(
      final Long userTaskKey,
      final String elementId,
      final String name,
      final String processDefinitionId,
      final OffsetDateTime creationDate,
      final OffsetDateTime completionDate,
      final String assignee,
      final UserTaskState state,
      final Long formKey,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final Long rootProcessInstanceKey,
      final String businessId,
      final Long elementInstanceKey,
      final String tenantId,
      final OffsetDateTime dueDate,
      final OffsetDateTime followUpDate,
      final String externalFormReference,
      final Integer processDefinitionVersion,
      final String serializedCustomHeaders,
      final Integer priority) {
    this(
        userTaskKey,
        elementId,
        name,
        processDefinitionId,
        creationDate,
        completionDate,
        assignee,
        state,
        formKey,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        businessId,
        elementInstanceKey,
        tenantId,
        dueDate,
        followUpDate,
        null,
        null,
        externalFormReference,
        processDefinitionVersion,
        serializedCustomHeaders,
        priority,
        null,
        0);
  }

  @Override
  public UserTaskDbModel copy(
      final Function<ObjectBuilder<UserTaskDbModel>, ObjectBuilder<UserTaskDbModel>> copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public Map<String, String> customHeaders() {
    return MapSerializer.deserialize(serializedCustomHeaders);
  }

  public Builder toBuilder() {
    return new Builder(serializedCustomHeaders)
        .userTaskKey(userTaskKey)
        .elementId(elementId)
        .name(name)
        .processDefinitionId(processDefinitionId)
        .creationDate(creationDate)
        .completionDate(completionDate)
        .assignee(assignee)
        .state(state)
        .formKey(formKey)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .businessId(businessId)
        .elementInstanceKey(elementInstanceKey)
        .tenantId(tenantId)
        .dueDate(dueDate)
        .followUpDate(followUpDate)
        .candidateGroups(candidateGroups)
        .candidateUsers(candidateUsers)
        .externalFormReference(externalFormReference)
        .processDefinitionVersion(processDefinitionVersion)
        .priority(priority)
        .tags(tags)
        .partitionId(partitionId);
  }

  public static class Builder implements ObjectBuilder<UserTaskDbModel> {

    private Long userTaskKey;
    private String elementId;
    private String name;
    private String processDefinitionId;
    private OffsetDateTime creationDate;
    private OffsetDateTime completionDate;
    private String assignee;
    private UserTaskDbModel.UserTaskState state;
    private Long formKey;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private String businessId;
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
    private Set<String> tags;
    private int partitionId;

    // Public constructor to initialize the builder
    public Builder() {}

    // Seeds the raw serialized column value for toBuilder()/copy(), so a copy that never touches
    // customHeaders() carries the original string through unparsed instead of round-tripping it
    // through Jackson. Package-private, not a public setter: a second public setter aliasing the
    // same field as customHeaders(Map) would let callers silently drop one write (e.g.
    // builder.serializedCustomHeaders(x).customHeaders(y) loses x).
    Builder(final String serializedCustomHeaders) {
      this.serializedCustomHeaders = serializedCustomHeaders;
    }

    public static UserTaskDbModel of(
        final Function<UserTaskDbModel.Builder, ObjectBuilder<UserTaskDbModel>> fn) {
      return fn.apply(new UserTaskDbModel.Builder()).build();
    }

    // Builder methods for each field
    public Builder userTaskKey(final Long userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder creationDate(final OffsetDateTime creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    public Builder completionDate(final OffsetDateTime completionDate) {
      this.completionDate = completionDate;
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

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder businessId(final String businessId) {
      this.businessId = businessId;
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
      serializedCustomHeaders = MapSerializer.serialize(customHeaders);
      return this;
    }

    public Builder priority(final int priority) {
      this.priority = priority;
      return this;
    }

    public Builder tags(final Set<String> tags) {
      this.tags = tags;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    // Build method to create the record
    @Override
    public UserTaskDbModel build() {
      return new UserTaskDbModel(
          userTaskKey,
          elementId,
          name,
          processDefinitionId,
          creationDate,
          completionDate,
          assignee,
          state,
          formKey,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          businessId,
          elementInstanceKey,
          tenantId,
          dueDate,
          followUpDate,
          candidateGroups,
          candidateUsers,
          externalFormReference,
          processDefinitionVersion,
          serializedCustomHeaders,
          priority,
          tags,
          partitionId);
    }
  }

  public enum UserTaskState {
    CREATING,
    CREATED,
    ASSIGNING,
    UPDATING,
    COMPLETING,
    COMPLETED,
    CANCELING,
    CANCELED,
    /**
     * The FAILED state is only applicable to legacy, job-worker-based user tasks. Native Camunda
     * User Tasks (non-job-worker-based) do not reach this state.
     */
    FAILED
  }
}
