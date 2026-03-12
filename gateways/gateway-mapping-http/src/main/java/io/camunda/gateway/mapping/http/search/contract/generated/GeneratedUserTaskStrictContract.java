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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskStrictContract(
    @Nullable String name,
    io.camunda.gateway.protocol.model.UserTaskStateEnum state,
    @Nullable String assignee,
    String elementId,
    java.util.List<String> candidateGroups,
    java.util.List<String> candidateUsers,
    String processDefinitionId,
    String creationDate,
    @Nullable String completionDate,
    @Nullable String followUpDate,
    @Nullable String dueDate,
    String tenantId,
    @Nullable String externalFormReference,
    Integer processDefinitionVersion,
    java.util.Map<String, String> customHeaders,
    Integer priority,
    String userTaskKey,
    String elementInstanceKey,
    @Nullable String processName,
    String processDefinitionKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    @Nullable String formKey,
    java.util.Set<String> tags) {

  public GeneratedUserTaskStrictContract {
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(candidateGroups, "candidateGroups is required and must not be null");
    Objects.requireNonNull(candidateUsers, "candidateUsers is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(creationDate, "creationDate is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(customHeaders, "customHeaders is required and must not be null");
    Objects.requireNonNull(priority, "priority is required and must not be null");
    Objects.requireNonNull(userTaskKey, "userTaskKey is required and must not be null");
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(tags, "tags is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private io.camunda.gateway.protocol.model.UserTaskStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
        statePolicy;
    private String assignee;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private java.util.List<String> candidateGroups;
    private ContractPolicy.FieldPolicy<java.util.List<String>> candidateGroupsPolicy;
    private java.util.List<String> candidateUsers;
    private ContractPolicy.FieldPolicy<java.util.List<String>> candidateUsersPolicy;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String creationDate;
    private ContractPolicy.FieldPolicy<String> creationDatePolicy;
    private String completionDate;
    private String followUpDate;
    private String dueDate;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String externalFormReference;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private java.util.Map<String, String> customHeaders;
    private ContractPolicy.FieldPolicy<java.util.Map<String, String>> customHeadersPolicy;
    private Integer priority;
    private ContractPolicy.FieldPolicy<Integer> priorityPolicy;
    private Object userTaskKey;
    private ContractPolicy.FieldPolicy<Object> userTaskKeyPolicy;
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;
    private String processName;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private Object formKey;
    private java.util.Set<String> tags;
    private ContractPolicy.FieldPolicy<java.util.Set<String>> tagsPolicy;

    private Builder() {}

    @Override
    public ElementIdStep state(
        final io.camunda.gateway.protocol.model.UserTaskStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public CandidateGroupsStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public CandidateUsersStep candidateGroups(
        final java.util.List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateGroups = candidateGroups;
      this.candidateGroupsPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateUsers = candidateUsers;
      this.candidateUsersPolicy = policy;
      return this;
    }

    @Override
    public CreationDateStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep creationDate(
        final String creationDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.creationDate = creationDate;
      this.creationDatePolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public CustomHeadersStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public PriorityStep customHeaders(
        final java.util.Map<String, String> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy) {
      this.customHeaders = customHeaders;
      this.customHeadersPolicy = policy;
      return this;
    }

    @Override
    public UserTaskKeyStep priority(
        final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = priority;
      this.priorityPolicy = policy;
      return this;
    }

    @Override
    public ElementInstanceKeyStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.userTaskKey = userTaskKey;
      this.userTaskKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public TagsStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy) {
      this.tags = tags;
      this.tagsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    @Override
    public OptionalStep assignee(
        final String assignee, final ContractPolicy.FieldPolicy<String> policy) {
      this.assignee = policy.apply(assignee, Fields.ASSIGNEE, null);
      return this;
    }

    @Override
    public OptionalStep completionDate(final String completionDate) {
      this.completionDate = completionDate;
      return this;
    }

    @Override
    public OptionalStep completionDate(
        final String completionDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.completionDate = policy.apply(completionDate, Fields.COMPLETION_DATE, null);
      return this;
    }

    @Override
    public OptionalStep followUpDate(final String followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    @Override
    public OptionalStep followUpDate(
        final String followUpDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.followUpDate = policy.apply(followUpDate, Fields.FOLLOW_UP_DATE, null);
      return this;
    }

    @Override
    public OptionalStep dueDate(final String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    @Override
    public OptionalStep dueDate(
        final String dueDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.dueDate = policy.apply(dueDate, Fields.DUE_DATE, null);
      return this;
    }

    @Override
    public OptionalStep externalFormReference(final String externalFormReference) {
      this.externalFormReference = externalFormReference;
      return this;
    }

    @Override
    public OptionalStep externalFormReference(
        final String externalFormReference, final ContractPolicy.FieldPolicy<String> policy) {
      this.externalFormReference =
          policy.apply(externalFormReference, Fields.EXTERNAL_FORM_REFERENCE, null);
      return this;
    }

    @Override
    public OptionalStep processName(final String processName) {
      this.processName = processName;
      return this;
    }

    @Override
    public OptionalStep processName(
        final String processName, final ContractPolicy.FieldPolicy<String> policy) {
      this.processName = policy.apply(processName, Fields.PROCESS_NAME, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(final String formKey) {
      this.formKey = formKey;
      return this;
    }

    @Override
    public OptionalStep formKey(final Object formKey) {
      this.formKey = formKey;
      return this;
    }

    public Builder formKey(final String formKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep formKey(
        final Object formKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.formKey = policy.apply(formKey, Fields.FORM_KEY, null);
      return this;
    }

    @Override
    public GeneratedUserTaskStrictContract build() {
      return new GeneratedUserTaskStrictContract(
          this.name,
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          this.assignee,
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          applyRequiredPolicy(
              this.candidateGroups, this.candidateGroupsPolicy, Fields.CANDIDATE_GROUPS),
          applyRequiredPolicy(
              this.candidateUsers, this.candidateUsersPolicy, Fields.CANDIDATE_USERS),
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.creationDate, this.creationDatePolicy, Fields.CREATION_DATE),
          this.completionDate,
          this.followUpDate,
          this.dueDate,
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          this.externalFormReference,
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          applyRequiredPolicy(this.customHeaders, this.customHeadersPolicy, Fields.CUSTOM_HEADERS),
          applyRequiredPolicy(this.priority, this.priorityPolicy, Fields.PRIORITY),
          coerceUserTaskKey(
              applyRequiredPolicy(this.userTaskKey, this.userTaskKeyPolicy, Fields.USER_TASK_KEY)),
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)),
          this.processName,
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceFormKey(this.formKey),
          applyRequiredPolicy(this.tags, this.tagsPolicy, Fields.TAGS));
    }
  }

  public interface StateStep {
    ElementIdStep state(
        final io.camunda.gateway.protocol.model.UserTaskStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy);
  }

  public interface ElementIdStep {
    CandidateGroupsStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CandidateGroupsStep {
    CandidateUsersStep candidateGroups(
        final java.util.List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface CandidateUsersStep {
    ProcessDefinitionIdStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface ProcessDefinitionIdStep {
    CreationDateStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CreationDateStep {
    TenantIdStep creationDate(
        final String creationDate, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    ProcessDefinitionVersionStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    CustomHeadersStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface CustomHeadersStep {
    PriorityStep customHeaders(
        final java.util.Map<String, String> customHeaders,
        final ContractPolicy.FieldPolicy<java.util.Map<String, String>> policy);
  }

  public interface PriorityStep {
    UserTaskKeyStep priority(
        final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface UserTaskKeyStep {
    ElementInstanceKeyStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ElementInstanceKeyStep {
    ProcessDefinitionKeyStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    TagsStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TagsStep {
    OptionalStep tags(
        final java.util.Set<String> tags,
        final ContractPolicy.FieldPolicy<java.util.Set<String>> policy);
  }

  public interface OptionalStep {
    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep assignee(final String assignee);

    OptionalStep assignee(final String assignee, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep completionDate(final String completionDate);

    OptionalStep completionDate(
        final String completionDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final String followUpDate);

    OptionalStep followUpDate(
        final String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep dueDate(final String dueDate);

    OptionalStep dueDate(final String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep externalFormReference(final String externalFormReference);

    OptionalStep externalFormReference(
        final String externalFormReference, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processName(final String processName);

    OptionalStep processName(
        final String processName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep formKey(final String formKey);

    OptionalStep formKey(final Object formKey);

    OptionalStep formKey(final String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(final Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

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
