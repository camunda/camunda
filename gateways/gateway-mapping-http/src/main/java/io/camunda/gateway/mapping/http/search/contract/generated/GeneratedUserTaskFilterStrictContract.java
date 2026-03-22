/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskFilterStrictContract(
    @JsonProperty("state") @Nullable GeneratedUserTaskStateFilterPropertyStrictContract state,
    @JsonProperty("assignee") @Nullable GeneratedStringFilterPropertyStrictContract assignee,
    @JsonProperty("priority") @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
    @JsonProperty("elementId") @Nullable String elementId,
    @JsonProperty("name") @Nullable GeneratedStringFilterPropertyStrictContract name,
    @JsonProperty("candidateGroup")
        @Nullable GeneratedStringFilterPropertyStrictContract candidateGroup,
    @JsonProperty("candidateUser")
        @Nullable GeneratedStringFilterPropertyStrictContract candidateUser,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
    @JsonProperty("processDefinitionId") @Nullable String processDefinitionId,
    @JsonProperty("creationDate")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract creationDate,
    @JsonProperty("completionDate")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract completionDate,
    @JsonProperty("followUpDate")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract followUpDate,
    @JsonProperty("dueDate") @Nullable GeneratedDateTimeFilterPropertyStrictContract dueDate,
    @JsonProperty("processInstanceVariables")
        java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables,
    @JsonProperty("localVariables")
        java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> localVariables,
    @JsonProperty("userTaskKey") @Nullable String userTaskKey,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("processInstanceKey") @Nullable String processInstanceKey,
    @JsonProperty("elementInstanceKey") @Nullable String elementInstanceKey,
    @JsonProperty("tags") java.util.@Nullable Set<String> tags) {

  public GeneratedUserTaskFilterStrictContract {
    if (processDefinitionId != null)
      if (processDefinitionId.isBlank())
        throw new IllegalArgumentException("processDefinitionId must not be blank");
    if (processDefinitionId != null)
      if (!processDefinitionId.matches("^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionId contains illegal characters. It must match the pattern '^[a-zA-Z_][a-zA-Z0-9_\\-\\.]*$'.");
    if (userTaskKey != null)
      if (userTaskKey.isBlank())
        throw new IllegalArgumentException("userTaskKey must not be blank");
    if (userTaskKey != null)
      if (userTaskKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided userTaskKey exceeds the limit of 25 characters.");
    if (userTaskKey != null)
      if (!userTaskKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided userTaskKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processDefinitionKey != null)
      if (processDefinitionKey.isBlank())
        throw new IllegalArgumentException("processDefinitionKey must not be blank");
    if (processDefinitionKey != null)
      if (processDefinitionKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processDefinitionKey exceeds the limit of 25 characters.");
    if (processDefinitionKey != null)
      if (!processDefinitionKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (processInstanceKey != null)
      if (processInstanceKey.isBlank())
        throw new IllegalArgumentException("processInstanceKey must not be blank");
    if (processInstanceKey != null)
      if (processInstanceKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processInstanceKey exceeds the limit of 25 characters.");
    if (processInstanceKey != null)
      if (!processInstanceKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (elementInstanceKey != null)
      if (elementInstanceKey.isBlank())
        throw new IllegalArgumentException("elementInstanceKey must not be blank");
    if (elementInstanceKey != null)
      if (elementInstanceKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided elementInstanceKey exceeds the limit of 25 characters.");
    if (elementInstanceKey != null)
      if (!elementInstanceKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided elementInstanceKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
    if (tags != null)
      if (tags.size() > 10) throw new IllegalArgumentException("tags must have at most 10 items");
  }

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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedUserTaskStateFilterPropertyStrictContract state;
    private GeneratedStringFilterPropertyStrictContract assignee;
    private GeneratedIntegerFilterPropertyStrictContract priority;
    private String elementId;
    private GeneratedStringFilterPropertyStrictContract name;
    private GeneratedStringFilterPropertyStrictContract candidateGroup;
    private GeneratedStringFilterPropertyStrictContract candidateUser;
    private GeneratedStringFilterPropertyStrictContract tenantId;
    private String processDefinitionId;
    private GeneratedDateTimeFilterPropertyStrictContract creationDate;
    private GeneratedDateTimeFilterPropertyStrictContract completionDate;
    private GeneratedDateTimeFilterPropertyStrictContract followUpDate;
    private GeneratedDateTimeFilterPropertyStrictContract dueDate;
    private Object processInstanceVariables;
    private Object localVariables;
    private Object userTaskKey;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object elementInstanceKey;
    private java.util.Set<String> tags;

    private Builder() {}

    @Override
    public OptionalStep state(
        final @Nullable GeneratedUserTaskStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedUserTaskStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep assignee(
        final @Nullable GeneratedStringFilterPropertyStrictContract assignee) {
      this.assignee = assignee;
      return this;
    }

    @Override
    public OptionalStep assignee(
        final @Nullable GeneratedStringFilterPropertyStrictContract assignee,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.assignee = policy.apply(assignee, Fields.ASSIGNEE, null);
      return this;
    }

    @Override
    public OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public OptionalStep elementId(final @Nullable String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep candidateGroup(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateGroup) {
      this.candidateGroup = candidateGroup;
      return this;
    }

    @Override
    public OptionalStep candidateGroup(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateGroup,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.candidateGroup = policy.apply(candidateGroup, Fields.CANDIDATE_GROUP, null);
      return this;
    }

    @Override
    public OptionalStep candidateUser(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateUser) {
      this.candidateUser = candidateUser;
      return this;
    }

    @Override
    public OptionalStep candidateUser(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateUser,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.candidateUser = policy.apply(candidateUser, Fields.CANDIDATE_USER, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep creationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    @Override
    public OptionalStep creationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.creationDate = policy.apply(creationDate, Fields.CREATION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep completionDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract completionDate) {
      this.completionDate = completionDate;
      return this;
    }

    @Override
    public OptionalStep completionDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract completionDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.completionDate = policy.apply(completionDate, Fields.COMPLETION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep followUpDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    @Override
    public OptionalStep followUpDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract followUpDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.followUpDate = policy.apply(followUpDate, Fields.FOLLOW_UP_DATE, null);
      return this;
    }

    @Override
    public OptionalStep dueDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    @Override
    public OptionalStep dueDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract dueDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.dueDate = policy.apply(dueDate, Fields.DUE_DATE, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables) {
      this.processInstanceVariables = processInstanceVariables;
      return this;
    }

    @Override
    public OptionalStep processInstanceVariables(final @Nullable Object processInstanceVariables) {
      this.processInstanceVariables = processInstanceVariables;
      return this;
    }

    public Builder processInstanceVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
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
        final @Nullable Object processInstanceVariables,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceVariables =
          policy.apply(processInstanceVariables, Fields.PROCESS_INSTANCE_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep localVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            localVariables) {
      this.localVariables = localVariables;
      return this;
    }

    @Override
    public OptionalStep localVariables(final @Nullable Object localVariables) {
      this.localVariables = localVariables;
      return this;
    }

    public Builder localVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            localVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy) {
      this.localVariables = policy.apply(localVariables, Fields.LOCAL_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep localVariables(
        final @Nullable Object localVariables, final ContractPolicy.FieldPolicy<Object> policy) {
      this.localVariables = policy.apply(localVariables, Fields.LOCAL_VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final @Nullable String userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public OptionalStep userTaskKey(final @Nullable Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder userTaskKey(
        final @Nullable String userTaskKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = policy.apply(userTaskKey, Fields.USER_TASK_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final @Nullable String processInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final @Nullable String elementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.@Nullable Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
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
    OptionalStep state(final @Nullable GeneratedUserTaskStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedUserTaskStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedUserTaskStateFilterPropertyStrictContract>
            policy);

    OptionalStep assignee(final @Nullable GeneratedStringFilterPropertyStrictContract assignee);

    OptionalStep assignee(
        final @Nullable GeneratedStringFilterPropertyStrictContract assignee,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep priority(final @Nullable GeneratedIntegerFilterPropertyStrictContract priority);

    OptionalStep priority(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract priority,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep elementId(final @Nullable String elementId);

    OptionalStep elementId(
        final @Nullable String elementId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name);

    OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep candidateGroup(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateGroup);

    OptionalStep candidateGroup(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateGroup,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep candidateUser(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateUser);

    OptionalStep candidateUser(
        final @Nullable GeneratedStringFilterPropertyStrictContract candidateUser,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionId(final @Nullable String processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep creationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationDate);

    OptionalStep creationDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract creationDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep completionDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract completionDate);

    OptionalStep completionDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract completionDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep followUpDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract followUpDate);

    OptionalStep followUpDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract followUpDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep dueDate(final @Nullable GeneratedDateTimeFilterPropertyStrictContract dueDate);

    OptionalStep dueDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract dueDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep processInstanceVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables);

    OptionalStep processInstanceVariables(final @Nullable Object processInstanceVariables);

    OptionalStep processInstanceVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            processInstanceVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep processInstanceVariables(
        final @Nullable Object processInstanceVariables,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep localVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            localVariables);

    OptionalStep localVariables(final @Nullable Object localVariables);

    OptionalStep localVariables(
        final java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract>
            localVariables,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedVariableValueFilterPropertyStrictContract>>
            policy);

    OptionalStep localVariables(
        final @Nullable Object localVariables, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep userTaskKey(final @Nullable String userTaskKey);

    OptionalStep userTaskKey(final @Nullable Object userTaskKey);

    OptionalStep userTaskKey(
        final @Nullable String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tags(final java.util.@Nullable Set<String> tags);

    OptionalStep tags(
        final java.util.@Nullable Set<String> tags,
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
