/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/UserTaskResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskStrictContract(
    @JsonProperty("name") @Nullable String name,
    @JsonProperty("state")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateEnum state,
    @JsonProperty("assignee") @Nullable String assignee,
    @JsonProperty("elementId") String elementId,
    @JsonProperty("candidateGroups") java.util.List<String> candidateGroups,
    @JsonProperty("candidateUsers") java.util.List<String> candidateUsers,
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("creationDate") String creationDate,
    @JsonProperty("completionDate") @Nullable String completionDate,
    @JsonProperty("followUpDate") @Nullable String followUpDate,
    @JsonProperty("dueDate") @Nullable String dueDate,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("externalFormReference") @Nullable String externalFormReference,
    @JsonProperty("processDefinitionVersion") Integer processDefinitionVersion,
    @JsonProperty("customHeaders") java.util.Map<String, String> customHeaders,
    @JsonProperty("priority") Integer priority,
    @JsonProperty("userTaskKey") String userTaskKey,
    @JsonProperty("elementInstanceKey") String elementInstanceKey,
    @JsonProperty("processName") @Nullable String processName,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("formKey") @Nullable String formKey,
    @JsonProperty("tags") java.util.Set<String> tags) {

  public GeneratedUserTaskStrictContract {
    Objects.requireNonNull(state, "No state provided.");
    Objects.requireNonNull(elementId, "No elementId provided.");
    Objects.requireNonNull(candidateGroups, "No candidateGroups provided.");
    Objects.requireNonNull(candidateUsers, "No candidateUsers provided.");
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(creationDate, "No creationDate provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(processDefinitionVersion, "No processDefinitionVersion provided.");
    Objects.requireNonNull(customHeaders, "No customHeaders provided.");
    Objects.requireNonNull(priority, "No priority provided.");
    Objects.requireNonNull(userTaskKey, "No userTaskKey provided.");
    Objects.requireNonNull(elementInstanceKey, "No elementInstanceKey provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
    Objects.requireNonNull(tags, "No tags provided.");
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

  public static String coerceRootProcessInstanceKey(final Object value) {
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
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceFormKey(final Object value) {
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
        "formKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static StateStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements StateStep,
          ElementIdStep,
          CandidateGroupsStep,
          CandidateUsersStep,
          ProcessDefinitionIdStep,
          CreationDateStep,
          TenantIdStep,
          ProcessDefinitionVersionStep,
          CustomHeadersStep,
          PriorityStep,
          UserTaskKeyStep,
          ElementInstanceKeyStep,
          ProcessDefinitionKeyStep,
          ProcessInstanceKeyStep,
          TagsStep,
          OptionalStep {
    private String name;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateEnum
        state;
    private String assignee;
    private String elementId;
    private java.util.List<String> candidateGroups;
    private java.util.List<String> candidateUsers;
    private String processDefinitionId;
    private String creationDate;
    private String completionDate;
    private String followUpDate;
    private String dueDate;
    private String tenantId;
    private String externalFormReference;
    private Integer processDefinitionVersion;
    private java.util.Map<String, String> customHeaders;
    private Integer priority;
    private Object userTaskKey;
    private Object elementInstanceKey;
    private String processName;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object formKey;
    private java.util.Set<String> tags;

    private Builder() {}

    @Override
    public ElementIdStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateEnum
            state) {
      this.state = state;
      return this;
    }

    @Override
    public CandidateGroupsStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public CandidateUsersStep candidateGroups(final java.util.List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep candidateUsers(final java.util.List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    @Override
    public CreationDateStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public TenantIdStep creationDate(final String creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public CustomHeadersStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public PriorityStep customHeaders(final java.util.Map<String, String> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    @Override
    public UserTaskKeyStep priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public ElementInstanceKeyStep userTaskKey(final Object userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public TagsStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep tags(final java.util.Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep assignee(final @Nullable String assignee) {
      this.assignee = assignee;
      return this;
    }

    @Override
    public OptionalStep assignee(
        final @Nullable String assignee, final ContractPolicy.FieldPolicy<String> policy) {
      this.assignee = policy.apply(assignee, Fields.ASSIGNEE, null);
      return this;
    }

    @Override
    public OptionalStep completionDate(final @Nullable String completionDate) {
      this.completionDate = completionDate;
      return this;
    }

    @Override
    public OptionalStep completionDate(
        final @Nullable String completionDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.completionDate = policy.apply(completionDate, Fields.COMPLETION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep followUpDate(final @Nullable String followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    @Override
    public OptionalStep followUpDate(
        final @Nullable String followUpDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.followUpDate = policy.apply(followUpDate, Fields.FOLLOW_UP_DATE, null);
      return this;
    }

    @Override
    public OptionalStep dueDate(final @Nullable String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    @Override
    public OptionalStep dueDate(
        final @Nullable String dueDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.dueDate = policy.apply(dueDate, Fields.DUE_DATE, null);
      return this;
    }

    @Override
    public OptionalStep externalFormReference(final @Nullable String externalFormReference) {
      this.externalFormReference = externalFormReference;
      return this;
    }

    @Override
    public OptionalStep externalFormReference(
        final @Nullable String externalFormReference,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.externalFormReference =
          policy.apply(externalFormReference, Fields.EXTERNAL_FORM_REFERENCE, null);
      return this;
    }

    @Override
    public OptionalStep processName(final @Nullable String processName) {
      this.processName = processName;
      return this;
    }

    @Override
    public OptionalStep processName(
        final @Nullable String processName, final ContractPolicy.FieldPolicy<String> policy) {
      this.processName = policy.apply(processName, Fields.PROCESS_NAME, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(final @Nullable String formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(final @Nullable Object formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder formKey(
        final @Nullable String formKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public GeneratedUserTaskStrictContract build() {
      return new GeneratedUserTaskStrictContract(
          this.name,
          this.state,
          this.assignee,
          this.elementId,
          this.candidateGroups,
          this.candidateUsers,
          this.processDefinitionId,
          this.creationDate,
          this.completionDate,
          this.followUpDate,
          this.dueDate,
          this.tenantId,
          this.externalFormReference,
          this.processDefinitionVersion,
          this.customHeaders,
          this.priority,
          coerceUserTaskKey(this.userTaskKey),
          coerceElementInstanceKey(this.elementInstanceKey),
          this.processName,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceFormKey(this.formKey),
          this.tags);
    }
  }

  public interface StateStep {
    ElementIdStep state(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateEnum
            state);
  }

  public interface ElementIdStep {
    CandidateGroupsStep elementId(final String elementId);
  }

  public interface CandidateGroupsStep {
    CandidateUsersStep candidateGroups(final java.util.List<String> candidateGroups);
  }

  public interface CandidateUsersStep {
    ProcessDefinitionIdStep candidateUsers(final java.util.List<String> candidateUsers);
  }

  public interface ProcessDefinitionIdStep {
    CreationDateStep processDefinitionId(final String processDefinitionId);
  }

  public interface CreationDateStep {
    TenantIdStep creationDate(final String creationDate);
  }

  public interface TenantIdStep {
    ProcessDefinitionVersionStep tenantId(final String tenantId);
  }

  public interface ProcessDefinitionVersionStep {
    CustomHeadersStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface CustomHeadersStep {
    PriorityStep customHeaders(final java.util.Map<String, String> customHeaders);
  }

  public interface PriorityStep {
    UserTaskKeyStep priority(final Integer priority);
  }

  public interface UserTaskKeyStep {
    ElementInstanceKeyStep userTaskKey(final Object userTaskKey);
  }

  public interface ElementInstanceKeyStep {
    ProcessDefinitionKeyStep elementInstanceKey(final Object elementInstanceKey);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstanceKeyStep {
    TagsStep processInstanceKey(final Object processInstanceKey);
  }

  public interface TagsStep {
    OptionalStep tags(final java.util.Set<String> tags);
  }

  public interface OptionalStep {
    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep assignee(final @Nullable String assignee);

    OptionalStep assignee(
        final @Nullable String assignee, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep completionDate(final @Nullable String completionDate);

    OptionalStep completionDate(
        final @Nullable String completionDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final @Nullable String followUpDate);

    OptionalStep followUpDate(
        final @Nullable String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep dueDate(final @Nullable String dueDate);

    OptionalStep dueDate(
        final @Nullable String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep externalFormReference(final @Nullable String externalFormReference);

    OptionalStep externalFormReference(
        final @Nullable String externalFormReference,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processName(final @Nullable String processName);

    OptionalStep processName(
        final @Nullable String processName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep formKey(final @Nullable String formKey);

    OptionalStep formKey(final @Nullable Object formKey);

    OptionalStep formKey(
        final @Nullable String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedUserTaskStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserTaskResult", "name");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("UserTaskResult", "state");
    public static final ContractPolicy.FieldRef ASSIGNEE =
        ContractPolicy.field("UserTaskResult", "assignee");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("UserTaskResult", "elementId");
    public static final ContractPolicy.FieldRef CANDIDATE_GROUPS =
        ContractPolicy.field("UserTaskResult", "candidateGroups");
    public static final ContractPolicy.FieldRef CANDIDATE_USERS =
        ContractPolicy.field("UserTaskResult", "candidateUsers");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("UserTaskResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef CREATION_DATE =
        ContractPolicy.field("UserTaskResult", "creationDate");
    public static final ContractPolicy.FieldRef COMPLETION_DATE =
        ContractPolicy.field("UserTaskResult", "completionDate");
    public static final ContractPolicy.FieldRef FOLLOW_UP_DATE =
        ContractPolicy.field("UserTaskResult", "followUpDate");
    public static final ContractPolicy.FieldRef DUE_DATE =
        ContractPolicy.field("UserTaskResult", "dueDate");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("UserTaskResult", "tenantId");
    public static final ContractPolicy.FieldRef EXTERNAL_FORM_REFERENCE =
        ContractPolicy.field("UserTaskResult", "externalFormReference");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("UserTaskResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef CUSTOM_HEADERS =
        ContractPolicy.field("UserTaskResult", "customHeaders");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("UserTaskResult", "priority");
    public static final ContractPolicy.FieldRef USER_TASK_KEY =
        ContractPolicy.field("UserTaskResult", "userTaskKey");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("UserTaskResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef PROCESS_NAME =
        ContractPolicy.field("UserTaskResult", "processName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("UserTaskResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("UserTaskResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("UserTaskResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("UserTaskResult", "formKey");
    public static final ContractPolicy.FieldRef TAGS =
        ContractPolicy.field("UserTaskResult", "tags");

    private Fields() {}
  }
}
