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
public record GeneratedUserTaskUpdateRequestStrictContract(
    @Nullable GeneratedChangesetStrictContract changeset, @Nullable String action) {

  public static GeneratedChangesetStrictContract coerceChangeset(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedChangesetStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "changeset must be a GeneratedChangesetStrictContract, but was "
            + value.getClass().getName());
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
    private Object changeset;
    private String action;

    private Builder() {}

    @Override
    public OptionalStep changeset(final GeneratedChangesetStrictContract changeset) {
      this.changeset = changeset;
      return this;
    }

    @Override
    public OptionalStep changeset(final Object changeset) {
      this.changeset = changeset;
      return this;
    }

    public Builder changeset(
        final GeneratedChangesetStrictContract changeset,
        final ContractPolicy.FieldPolicy<GeneratedChangesetStrictContract> policy) {
      this.changeset = policy.apply(changeset, Fields.CHANGESET, null);
      return this;
    }

    @Override
    public OptionalStep changeset(
        final Object changeset, final ContractPolicy.FieldPolicy<Object> policy) {
      this.changeset = policy.apply(changeset, Fields.CHANGESET, null);
      return this;
    }

    @Override
    public OptionalStep action(final String action) {
      this.action = action;
      return this;
    }

    @Override
    public OptionalStep action(
        final String action, final ContractPolicy.FieldPolicy<String> policy) {
      this.action = policy.apply(action, Fields.ACTION, null);
      return this;
    }

    @Override
    public GeneratedUserTaskUpdateRequestStrictContract build() {
      return new GeneratedUserTaskUpdateRequestStrictContract(
          coerceChangeset(this.changeset), this.action);
    }
  }

  public interface OptionalStep {
    OptionalStep changeset(final GeneratedChangesetStrictContract changeset);

    OptionalStep changeset(final Object changeset);

    OptionalStep changeset(
        final GeneratedChangesetStrictContract changeset,
        final ContractPolicy.FieldPolicy<GeneratedChangesetStrictContract> policy);

    OptionalStep changeset(final Object changeset, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep action(final String action);

    OptionalStep action(final String action, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserTaskUpdateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CHANGESET =
        ContractPolicy.field("UserTaskUpdateRequest", "changeset");
    public static final ContractPolicy.FieldRef ACTION =
        ContractPolicy.field("UserTaskUpdateRequest", "action");

    private Fields() {}
  }
}
