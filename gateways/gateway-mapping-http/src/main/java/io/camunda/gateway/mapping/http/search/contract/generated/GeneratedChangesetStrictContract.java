/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedChangesetStrictContract(
    @Nullable String dueDate,
    @Nullable String followUpDate,
    @Nullable java.util.List<String> candidateUsers,
    @Nullable java.util.List<String> candidateGroups,
    @Nullable Integer priority) {

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
    private String dueDate;
    private String followUpDate;
    private java.util.List<String> candidateUsers;
    private java.util.List<String> candidateGroups;
    private Integer priority;

    private Builder() {}

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
    public OptionalStep candidateUsers(final java.util.List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    @Override
    public OptionalStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateUsers = policy.apply(candidateUsers, Fields.CANDIDATE_USERS, null);
      return this;
    }

    @Override
    public OptionalStep candidateGroups(final java.util.List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    @Override
    public OptionalStep candidateGroups(
        final java.util.List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.candidateGroups = policy.apply(candidateGroups, Fields.CANDIDATE_GROUPS, null);
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
    OptionalStep dueDate(final String dueDate);

    OptionalStep dueDate(final String dueDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep followUpDate(final String followUpDate);

    OptionalStep followUpDate(
        final String followUpDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep candidateUsers(final java.util.List<String> candidateUsers);

    OptionalStep candidateUsers(
        final java.util.List<String> candidateUsers,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep candidateGroups(final java.util.List<String> candidateGroups);

    OptionalStep candidateGroups(
        final java.util.List<String> candidateGroups,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep priority(final Integer priority);

    OptionalStep priority(final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

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
