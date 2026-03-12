/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskFilterStrictContract(
    @Nullable Object state,
    @Nullable Object assignee,
    @Nullable Object priority,
    @Nullable String elementId,
    @Nullable Object name,
    @Nullable Object candidateGroup,
    @Nullable Object candidateUser,
    @Nullable Object tenantId,
    @Nullable String processDefinitionId,
    @Nullable Object creationDate,
    @Nullable Object completionDate,
    @Nullable Object followUpDate,
    @Nullable Object dueDate,
    @Nullable
        java.util.List<GeneratedVariableValueFilterPropertyStrictContract> processInstanceVariables,
    @Nullable java.util.List<GeneratedVariableValueFilterPropertyStrictContract> localVariables,
    @Nullable String userTaskKey,
    @Nullable String processDefinitionKey,
    @Nullable String processInstanceKey,
    @Nullable String elementInstanceKey,
    @Nullable java.util.Set<String> tags) {

  public static java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
      coerceProcessInstanceVariables(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "processInstanceVariables must be a List of GeneratedVariableValueFilterPropertyStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedVariableValueFilterPropertyStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedVariableValueFilterPropertyStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "processInstanceVariables must contain only GeneratedVariableValueFilterPropertyStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
      coerceLocalVariables(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "localVariables must be a List of GeneratedVariableValueFilterPropertyStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedVariableValueFilterPropertyStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedVariableValueFilterPropertyStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "localVariables must contain only GeneratedVariableValueFilterPropertyStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static String coerceUserTaskKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "userTaskKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceElementInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object state;
    private Object assignee;
    private Object priority;
    private String elementId;
    private Object name;
    private Object candidateGroup;
    private Object candidateUser;
    private Object tenantId;
    private String processDefinitionId;
    private Object creationDate;
    private Object completionDate;
    private Object followUpDate;
    private Object dueDate;
    private Object processInstanceVariables;
    private Object localVariables;
    private Object userTaskKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object elementInstanceKey;
    private java.util.Set<String> tags;

    private Builder() {}

    @Override
    public OptionalStep state(final Object state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep assignee(final Object assignee) {
      this.assignee = assignee;
      return this;
    }

    @Override
    public OptionalStep assignee(
        final Object assignee, final ContractPolicy.FieldPolicy<Object> policy) {
      this.assignee = policy.apply(assignee, Fields.ASSIGNEE, null);
      return this;
    }

    @Override
    public OptionalStep priority(final Object priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final Object priority, final ContractPolicy.FieldPolicy<Object> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep name(final Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep candidateGroup(final Object candidateGroup) {
      this.candidateGroup = candidateGroup;
      return this;
    }

    @Override
    public OptionalStep candidateGroup(
        final Object candidateGroup, final ContractPolicy.FieldPolicy<Object> policy) {
      this.candidateGroup = policy.apply(candidateGroup, Fields.CANDIDATE_GROUP, null);
      return this;
    }

    @Override
    public OptionalStep candidateUser(final Object candidateUser) {
      this.candidateUser = candidateUser;
      return this;
    }

    @Override
    public OptionalStep candidateUser(
        final Object candidateUser, final ContractPolicy.FieldPolicy<Object> policy) {
      this.candidateUser = policy.apply(candidateUser, Fields.CANDIDATE_USER, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep creationDate(final Object creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    @Override
    public OptionalStep creationDate(
        final Object creationDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.creationDate = policy.apply(creationDate, Fields.CREATION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep completionDate(final Object completionDate) {
      this.completionDate = completionDate;
      return this;
    }

    @Override
    public OptionalStep completionDate(
        final Object completionDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.completionDate = policy.apply(completionDate, Fields.COMPLETION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep followUpDate(final Object followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    @Override
    public OptionalStep followUpDate(
        final Object followUpDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.followUpDate = policy.apply(followUpDate, Fields.FOLLOW_UP_DATE, null);
      return this;
    }

    @Override
    public OptionalStep dueDate(final Object dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    @Override
    public OptionalStep dueDate(
        final Object dueDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.dueDate = policy.apply(dueDate, Fields.DUE_DATE, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables) {
      this.processInstanceVariables = processInstanceVariables;
      return this;
    }

    @Override
    public OptionalStep processInstanceVariables(final Object processInstanceVariables) {
      this.processInstanceVariables = processInstanceVariables;
      return this;
    }

    public Builder processInstanceVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy) {
      this.processInstanceVariables =
          policy.apply(processInstanceVariables, Fields.PROCESS_INSTANCE_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceVariables(
        final Object processInstanceVariables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceVariables =
          policy.apply(processInstanceVariables, Fields.PROCESS_INSTANCE_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep localVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> localVariables) {
      this.localVariables = localVariables;
      return this;
    }

    @Override
    public OptionalStep localVariables(final Object localVariables) {
      this.localVariables = localVariables;
      return this;
    }

    public Builder localVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> localVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy) {
      this.localVariables = policy.apply(localVariables, Fields.LOCAL_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep localVariables(
        final Object localVariables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.localVariables = policy.apply(localVariables, Fields.LOCAL_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final String userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder userTaskKey(
        final String userTaskKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = policy.apply(tags, Fields.TAGS, null);
      return this;
    }

    @Override
    public GeneratedUserTaskFilterStrictContract build() {
      return new GeneratedUserTaskFilterStrictContract(
          this.state,
          this.assignee,
          this.priority,
          this.elementId,
          this.name,
          this.candidateGroup,
          this.candidateUser,
          this.tenantId,
          this.processDefinitionId,
          this.creationDate,
          this.completionDate,
          this.followUpDate,
          this.dueDate,
          coerceProcessInstanceVariables(this.processInstanceVariables),
          coerceLocalVariables(this.localVariables),
          coerceUserTaskKey(this.userTaskKey),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          this.tags);
    }
  }

  public interface OptionalStep {
    OptionalStep state(final Object state);

    OptionalStep state(final Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep assignee(final Object assignee);

    OptionalStep assignee(final Object assignee, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep priority(final Object priority);

    OptionalStep priority(final Object priority, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final String elementId);

    OptionalStep elementId(final String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final Object name);

    OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep candidateGroup(final Object candidateGroup);

    OptionalStep candidateGroup(
        final Object candidateGroup, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep candidateUser(final Object candidateUser);

    OptionalStep candidateUser(
        final Object candidateUser, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final Object tenantId);

    OptionalStep tenantId(final Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionId(final String processDefinitionId);

    OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep creationDate(final Object creationDate);

    OptionalStep creationDate(
        final Object creationDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep completionDate(final Object completionDate);

    OptionalStep completionDate(
        final Object completionDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep followUpDate(final Object followUpDate);

    OptionalStep followUpDate(
        final Object followUpDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep dueDate(final Object dueDate);

    OptionalStep dueDate(final Object dueDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables);

    OptionalStep processInstanceVariables(final Object processInstanceVariables);

    OptionalStep processInstanceVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep processInstanceVariables(
        final Object processInstanceVariables, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep localVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> localVariables);

    OptionalStep localVariables(final Object localVariables);

    OptionalStep localVariables(
        final java.util.List<GeneratedVariableValueFilterPropertyStrictContract> localVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep localVariables(
        final Object localVariables, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep userTaskKey(final String userTaskKey);

    OptionalStep userTaskKey(final Object userTaskKey);

    OptionalStep userTaskKey(
        final String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final String processDefinitionKey);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tags(final java.util.Set<String> tags);

    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);

    GeneratedUserTaskFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("UserTaskFilter", "state");
    public static final ContractPolicy.FieldRef ASSIGNEE =
        ContractPolicy.field("UserTaskFilter", "assignee");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("UserTaskFilter", "priority");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("UserTaskFilter", "elementId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserTaskFilter", "name");
    public static final ContractPolicy.FieldRef CANDIDATE_GROUP =
        ContractPolicy.field("UserTaskFilter", "candidateGroup");
    public static final ContractPolicy.FieldRef CANDIDATE_USER =
        ContractPolicy.field("UserTaskFilter", "candidateUser");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("UserTaskFilter", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("UserTaskFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef CREATION_DATE =
        ContractPolicy.field("UserTaskFilter", "creationDate");
    public static final ContractPolicy.FieldRef COMPLETION_DATE =
        ContractPolicy.field("UserTaskFilter", "completionDate");
    public static final ContractPolicy.FieldRef FOLLOW_UP_DATE =
        ContractPolicy.field("UserTaskFilter", "followUpDate");
    public static final ContractPolicy.FieldRef DUE_DATE =
        ContractPolicy.field("UserTaskFilter", "dueDate");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_VARIABLES =
        ContractPolicy.field("UserTaskFilter", "processInstanceVariables");
    public static final ContractPolicy.FieldRef LOCAL_VARIABLES =
        ContractPolicy.field("UserTaskFilter", "localVariables");
    public static final ContractPolicy.FieldRef USER_TASK_KEY =
        ContractPolicy.field("UserTaskFilter", "userTaskKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("UserTaskFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("UserTaskFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("UserTaskFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("UserTaskFilter", "tags");

    private Fields() {}
  }
}
