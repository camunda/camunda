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
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FilterStep builder() {
    return new Builder();
  }

  public static final class Builder implements FilterStep, MoveInstructionsStep, OptionalStep {
    private Object filter;
    private ContractPolicy.FieldPolicy<Object> filterPolicy;
    private Object moveInstructions;
    private ContractPolicy.FieldPolicy<Object> moveInstructionsPolicy;
    private Long operationReference;

    private Builder() {}

    @Override
    public MoveInstructionsStep filter(
        final Object filter, final ContractPolicy.FieldPolicy<Object> policy) {
      this.filter = filter;
      this.filterPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep moveInstructions(
        final Object moveInstructions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.moveInstructions = moveInstructions;
      this.moveInstructionsPolicy = policy;
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
    public GeneratedProcessInstanceModificationBatchOperationRequestStrictContract build() {
      return new GeneratedProcessInstanceModificationBatchOperationRequestStrictContract(
          coerceFilter(applyRequiredPolicy(this.filter, this.filterPolicy, Fields.FILTER)),
          coerceMoveInstructions(
              applyRequiredPolicy(
                  this.moveInstructions, this.moveInstructionsPolicy, Fields.MOVE_INSTRUCTIONS)),
          this.operationReference);
    }
  }

  public interface FilterStep {
    MoveInstructionsStep filter(
        final Object filter, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface MoveInstructionsStep {
    OptionalStep moveInstructions(
        final Object moveInstructions, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep operationReference(final Long operationReference);

    OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

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
