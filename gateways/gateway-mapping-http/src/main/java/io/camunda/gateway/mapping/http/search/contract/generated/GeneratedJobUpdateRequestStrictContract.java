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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobUpdateRequestStrictContract(
    GeneratedJobChangesetStrictContract changeset, @Nullable Long operationReference) {

  public GeneratedJobUpdateRequestStrictContract {
    Objects.requireNonNull(changeset, "changeset is required and must not be null");
  }

  public static GeneratedJobChangesetStrictContract coerceChangeset(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedJobChangesetStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "changeset must be a GeneratedJobChangesetStrictContract, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ChangesetStep builder() {
    return new Builder();
  }

  public static final class Builder implements ChangesetStep, OptionalStep {
    private Object changeset;
    private ContractPolicy.FieldPolicy<Object> changesetPolicy;
    private Long operationReference;

    private Builder() {}

    @Override
    public OptionalStep changeset(
        final Object changeset, final ContractPolicy.FieldPolicy<Object> policy) {
      this.changeset = changeset;
      this.changesetPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep operationReference(final Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public GeneratedJobUpdateRequestStrictContract build() {
      return new GeneratedJobUpdateRequestStrictContract(
          coerceChangeset(
              applyRequiredPolicy(this.changeset, this.changesetPolicy, Fields.CHANGESET)),
          this.operationReference);
    }
  }

  public interface ChangesetStep {
    OptionalStep changeset(final Object changeset, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep operationReference(final Long operationReference);

    OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedJobUpdateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CHANGESET =
        ContractPolicy.field("JobUpdateRequest", "changeset");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("JobUpdateRequest", "operationReference");

    private Fields() {}
  }
}
