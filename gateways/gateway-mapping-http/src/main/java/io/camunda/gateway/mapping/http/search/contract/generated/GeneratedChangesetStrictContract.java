/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/Changeset
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedChangesetStrictContract(
    @JsonProperty("dueDate") @Nullable String dueDate,
    @JsonProperty("followUpDate") @Nullable String followUpDate,
    @JsonProperty("candidateUsers") java.util.@Nullable List<String> candidateUsers,
    @JsonProperty("candidateGroups") java.util.@Nullable List<String> candidateGroups,
    @JsonProperty("priority") @Nullable Integer priority) {

  public GeneratedChangesetStrictContract {
    if (priority != null)
      if (priority < 0)
        throw new IllegalArgumentException(
            "The value for priority is '" + priority + "' but must be not negative.");
    if (priority != null)
      if (priority > 100)
        throw new IllegalArgumentException(
            "The value for priority is '" + priority + "' but must be at most 100.");
    if (priority == null) priority = 50;
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String dueDate;
    private String followUpDate;
    private java.util.List<String> candidateUsers;
    private java.util.List<String> candidateGroups;
    private Integer priority;

    private Builder() {}

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
    public OptionalStep candidateUsers(final java.util.@Nullable List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    @Override
    public OptionalStep candidateUsers(
        final java.util.@Nullable List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateUsers = policy.apply(candidateUsers, Fields.CANDIDATE_USERS, null);
      return this;
    }

    @Override
    public OptionalStep candidateGroups(final java.util.@Nullable List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    @Override
    public OptionalStep candidateGroups(
        final java.util.@Nullable List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateGroups = policy.apply(candidateGroups, Fields.CANDIDATE_GROUPS, null);
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
    public GeneratedChangesetStrictContract build() {
      return new GeneratedChangesetStrictContract(
          this.dueDate,
          this.followUpDate,
          this.candidateUsers,
          this.candidateGroups,
          this.priority);
    }
  }

  public interface OptionalStep {
    OptionalStep dueDate(final @Nullable String dueDate);

    OptionalStep dueDate(
        final @Nullable String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final @Nullable String followUpDate);

    OptionalStep followUpDate(
        final @Nullable String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep candidateUsers(final java.util.@Nullable List<String> candidateUsers);

    OptionalStep candidateUsers(
        final java.util.@Nullable List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep candidateGroups(final java.util.@Nullable List<String> candidateGroups);

    OptionalStep candidateGroups(
        final java.util.@Nullable List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep priority(final @Nullable Integer priority);

    OptionalStep priority(
        final @Nullable Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedChangesetStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DUE_DATE =
        ContractPolicy.field("Changeset", "dueDate");
    public static final ContractPolicy.FieldRef FOLLOW_UP_DATE =
        ContractPolicy.field("Changeset", "followUpDate");
    public static final ContractPolicy.FieldRef CANDIDATE_USERS =
        ContractPolicy.field("Changeset", "candidateUsers");
    public static final ContractPolicy.FieldRef CANDIDATE_GROUPS =
        ContractPolicy.field("Changeset", "candidateGroups");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("Changeset", "priority");

    private Fields() {}
  }
}
