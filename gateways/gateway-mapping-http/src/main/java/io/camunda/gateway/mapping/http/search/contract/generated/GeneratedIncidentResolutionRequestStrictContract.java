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
public record GeneratedIncidentResolutionRequestStrictContract(@Nullable Long operationReference) {

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
    private Long operationReference;

    private Builder() {}

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
    public GeneratedIncidentResolutionRequestStrictContract build() {
      return new GeneratedIncidentResolutionRequestStrictContract(this.operationReference);
    }
  }

  public interface OptionalStep {
    OptionalStep operationReference(final Long operationReference);

    OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedIncidentResolutionRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("IncidentResolutionRequest", "operationReference");

    private Fields() {}
  }
}
