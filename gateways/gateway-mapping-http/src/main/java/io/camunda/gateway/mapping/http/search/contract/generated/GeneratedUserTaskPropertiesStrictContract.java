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
public record GeneratedUserTaskPropertiesStrictContract(
    String action,
    @Nullable String assignee,
    java.util.List<String> candidateGroups,
    java.util.List<String> candidateUsers,
    java.util.List<String> changedAttributes,
    @Nullable String dueDate,
    @Nullable String followUpDate,
    @Nullable String formKey,
    @Nullable Integer priority,
    @Nullable String userTaskKey) {

  public GeneratedUserTaskPropertiesStrictContract {
    Objects.requireNonNull(action, "action is required and must not be null");
    Objects.requireNonNull(candidateGroups, "candidateGroups is required and must not be null");
    Objects.requireNonNull(candidateUsers, "candidateUsers is required and must not be null");
    Objects.requireNonNull(changedAttributes, "changedAttributes is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ActionStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ActionStep,
          CandidateGroupsStep,
          CandidateUsersStep,
          ChangedAttributesStep,
          OptionalStep {
    private String action;
    private ContractPolicy.FieldPolicy<String> actionPolicy;
    private String assignee;
    private java.util.List<String> candidateGroups;
    private ContractPolicy.FieldPolicy<java.util.List<String>> candidateGroupsPolicy;
    private java.util.List<String> candidateUsers;
    private ContractPolicy.FieldPolicy<java.util.List<String>> candidateUsersPolicy;
    private java.util.List<String> changedAttributes;
    private ContractPolicy.FieldPolicy<java.util.List<String>> changedAttributesPolicy;
    private String dueDate;
    private String followUpDate;
    private Object formKey;
    private Integer priority;
    private Object userTaskKey;

    private Builder() {}

    @Override
    public CandidateGroupsStep action(
        final String action, final ContractPolicy.FieldPolicy<String> policy) {
      this.action = action;
      this.actionPolicy = policy;
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
    public ChangedAttributesStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateUsers = candidateUsers;
      this.candidateUsersPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep changedAttributes(
        final java.util.List<String> changedAttributes,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.changedAttributes = changedAttributes;
      this.changedAttributesPolicy = policy;
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
    public OptionalStep priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
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
    public GeneratedUserTaskPropertiesStrictContract build() {
      return new GeneratedUserTaskPropertiesStrictContract(
          applyRequiredPolicy(this.action, this.actionPolicy, Fields.ACTION),
          this.assignee,
          applyRequiredPolicy(
              this.candidateGroups, this.candidateGroupsPolicy, Fields.CANDIDATE_GROUPS),
          applyRequiredPolicy(
              this.candidateUsers, this.candidateUsersPolicy, Fields.CANDIDATE_USERS),
          applyRequiredPolicy(
              this.changedAttributes, this.changedAttributesPolicy, Fields.CHANGED_ATTRIBUTES),
          this.dueDate,
          this.followUpDate,
          coerceFormKey(this.formKey),
          this.priority,
          coerceUserTaskKey(this.userTaskKey));
    }
  }

  public interface ActionStep {
    CandidateGroupsStep action(
        final String action, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface CandidateGroupsStep {
    CandidateUsersStep candidateGroups(
        final java.util.List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface CandidateUsersStep {
    ChangedAttributesStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface ChangedAttributesStep {
    OptionalStep changedAttributes(
        final java.util.List<String> changedAttributes,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface OptionalStep {
    OptionalStep assignee(final String assignee);

    OptionalStep assignee(final String assignee, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep dueDate(final String dueDate);

    OptionalStep dueDate(final String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final String followUpDate);

    OptionalStep followUpDate(
        final String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(final String formKey);

    OptionalStep formKey(final Object formKey);

    OptionalStep formKey(final String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(final Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep priority(final Integer priority);

    OptionalStep priority(final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep userTaskKey(final String userTaskKey);

    OptionalStep userTaskKey(final Object userTaskKey);

    OptionalStep userTaskKey(
        final String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedUserTaskPropertiesStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ACTION =
        ContractPolicy.field("UserTaskProperties", "action");
    public static final ContractPolicy.FieldRef ASSIGNEE =
        ContractPolicy.field("UserTaskProperties", "assignee");
    public static final ContractPolicy.FieldRef CANDIDATE_GROUPS =
        ContractPolicy.field("UserTaskProperties", "candidateGroups");
    public static final ContractPolicy.FieldRef CANDIDATE_USERS =
        ContractPolicy.field("UserTaskProperties", "candidateUsers");
    public static final ContractPolicy.FieldRef CHANGED_ATTRIBUTES =
        ContractPolicy.field("UserTaskProperties", "changedAttributes");
    public static final ContractPolicy.FieldRef DUE_DATE =
        ContractPolicy.field("UserTaskProperties", "dueDate");
    public static final ContractPolicy.FieldRef FOLLOW_UP_DATE =
        ContractPolicy.field("UserTaskProperties", "followUpDate");
    public static final ContractPolicy.FieldRef FORM_KEY =
        ContractPolicy.field("UserTaskProperties", "formKey");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("UserTaskProperties", "priority");
    public static final ContractPolicy.FieldRef USER_TASK_KEY =
        ContractPolicy.field("UserTaskProperties", "userTaskKey");

    private Fields() {}
  }
}
