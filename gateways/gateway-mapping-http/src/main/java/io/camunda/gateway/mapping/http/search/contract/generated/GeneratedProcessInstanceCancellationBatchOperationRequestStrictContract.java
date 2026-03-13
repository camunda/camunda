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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract(
    GeneratedProcessInstanceFilterFieldsStrictContract filter, @Nullable Long operationReference) {

  public GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract {
    Objects.requireNonNull(filter, "filter is required and must not be null");
  }

  public static GeneratedProcessInstanceFilterFieldsStrictContract coerceFilter(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedProcessInstanceFilterFieldsStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "filter must be a GeneratedProcessInstanceFilterFieldsStrictContract, but was "
            + value.getClass().getName());
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, OptionalStep {
    private Object filter;
    private Long operationReference;

    private Builder() {}

    @Override
    public OptionalStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep operationReference(final @Nullable Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract build() {
      return new GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract(
          coerceFilter(this.filter), this.operationReference);
    }
  }

  public interface FilterStep {
    OptionalStep filter(final Object filter);
  }

  public interface OptionalStep {
    OptionalStep operationReference(final @Nullable Long operationReference);

    OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedProcessInstanceCancellationBatchOperationRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ProcessInstanceCancellationBatchOperationRequest", "filter");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field(
            "ProcessInstanceCancellationBatchOperationRequest", "operationReference");

    private Fields() {}
  }
}
