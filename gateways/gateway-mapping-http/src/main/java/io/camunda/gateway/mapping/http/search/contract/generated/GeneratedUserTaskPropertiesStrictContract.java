/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml#/components/schemas/UserTaskProperties
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
public record GeneratedUserTaskPropertiesStrictContract(
    @JsonProperty("action") String action,
    @JsonProperty("assignee") @Nullable String assignee,
    @JsonProperty("candidateGroups") java.util.List<String> candidateGroups,
    @JsonProperty("candidateUsers") java.util.List<String> candidateUsers,
    @JsonProperty("changedAttributes") java.util.List<String> changedAttributes,
    @JsonProperty("dueDate") @Nullable String dueDate,
    @JsonProperty("followUpDate") @Nullable String followUpDate,
    @JsonProperty("formKey") @Nullable String formKey,
    @JsonProperty("priority") @Nullable Integer priority,
    @JsonProperty("userTaskKey") @Nullable String userTaskKey) {

  public GeneratedUserTaskPropertiesStrictContract {
    Objects.requireNonNull(action, "No action provided.");
    Objects.requireNonNull(candidateGroups, "No candidateGroups provided.");
    Objects.requireNonNull(candidateUsers, "No candidateUsers provided.");
    Objects.requireNonNull(changedAttributes, "No changedAttributes provided.");
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
    private String assignee;
    private java.util.List<String> candidateGroups;
    private java.util.List<String> candidateUsers;
    private java.util.List<String> changedAttributes;
    private String dueDate;
    private String followUpDate;
    private Object formKey;
    private Integer priority;
    private Object userTaskKey;

    private Builder() {}

    @Override
    public CandidateGroupsStep action(final String action) {
      this.action = action;
      return this;
    }

    @Override
    public CandidateUsersStep candidateGroups(final java.util.List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    @Override
    public ChangedAttributesStep candidateUsers(final java.util.List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    @Override
    public OptionalStep changedAttributes(final java.util.List<String> changedAttributes) {
      this.changedAttributes = changedAttributes;
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
    public OptionalStep priority(final @Nullable Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
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
    public GeneratedUserTaskPropertiesStrictContract build() {
      return new GeneratedUserTaskPropertiesStrictContract(
          this.action,
          this.assignee,
          this.candidateGroups,
          this.candidateUsers,
          this.changedAttributes,
          this.dueDate,
          this.followUpDate,
          coerceFormKey(this.formKey),
          this.priority,
          coerceUserTaskKey(this.userTaskKey));
    }
  }

  public interface ActionStep {
    CandidateGroupsStep action(final String action);
  }

  public interface CandidateGroupsStep {
    CandidateUsersStep candidateGroups(final java.util.List<String> candidateGroups);
  }

  public interface CandidateUsersStep {
    ChangedAttributesStep candidateUsers(final java.util.List<String> candidateUsers);
  }

  public interface ChangedAttributesStep {
    OptionalStep changedAttributes(final java.util.List<String> changedAttributes);
  }

  public interface OptionalStep {
    OptionalStep assignee(final @Nullable String assignee);

    OptionalStep assignee(
        final @Nullable String assignee, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep dueDate(final @Nullable String dueDate);

    OptionalStep dueDate(
        final @Nullable String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final @Nullable String followUpDate);

    OptionalStep followUpDate(
        final @Nullable String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(final @Nullable String formKey);

    OptionalStep formKey(final @Nullable Object formKey);

    OptionalStep formKey(
        final @Nullable String formKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep formKey(
        final @Nullable Object formKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep priority(final @Nullable Integer priority);

    OptionalStep priority(
        final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep userTaskKey(final @Nullable String userTaskKey);

    OptionalStep userTaskKey(final @Nullable Object userTaskKey);

    OptionalStep userTaskKey(
        final @Nullable String userTaskKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep userTaskKey(
        final @Nullable Object userTaskKey, final ContractPolicy.FieldPolicy<Object> policy);

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
