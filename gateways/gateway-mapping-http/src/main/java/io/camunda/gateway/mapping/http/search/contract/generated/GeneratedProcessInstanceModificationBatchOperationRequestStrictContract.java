/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationBatchOperationRequestStrictContract(
    GeneratedProcessInstanceFilterFieldsStrictContract filter,
    java.util.List<GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract>
        moveInstructions,
    @Nullable Long operationReference) {

  public GeneratedProcessInstanceModificationBatchOperationRequestStrictContract {
    Objects.requireNonNull(filter, "filter is required and must not be null");
    Objects.requireNonNull(moveInstructions, "moveInstructions is required and must not be null");
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

  public static java.util.List<
          GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract>
      coerceMoveInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "moveInstructions must be a List of GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<
            GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof
          GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract
              strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "moveInstructions must contain only GeneratedProcessInstanceModificationMoveBatchOperationInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, MoveInstructionsStep, OptionalStep {
    private Object filter;
    private Object moveInstructions;
    private Long operationReference;

    private Builder() {}

    @Override
    public MoveInstructionsStep filter(final Object filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public OptionalStep moveInstructions(final Object moveInstructions) {
      this.moveInstructions = moveInstructions;
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
    public GeneratedProcessInstanceModificationBatchOperationRequestStrictContract build() {
      return new GeneratedProcessInstanceModificationBatchOperationRequestStrictContract(
          coerceFilter(this.filter),
          coerceMoveInstructions(this.moveInstructions),
          this.operationReference);
    }
  }

  public interface FilterStep {
    MoveInstructionsStep filter(final Object filter);
  }

  public interface MoveInstructionsStep {
    OptionalStep moveInstructions(final Object moveInstructions);
  }

  public interface OptionalStep {
    OptionalStep operationReference(final @Nullable Long operationReference);

    OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedProcessInstanceModificationBatchOperationRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILTER =
        ContractPolicy.field("ProcessInstanceModificationBatchOperationRequest", "filter");
    public static final ContractPolicy.FieldRef MOVE_INSTRUCTIONS =
        ContractPolicy.field(
            "ProcessInstanceModificationBatchOperationRequest", "moveInstructions");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field(
            "ProcessInstanceModificationBatchOperationRequest", "operationReference");

    private Fields() {}
  }
}
