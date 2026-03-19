/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceModificationInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationInstructionStrictContract(
    @JsonProperty("operationReference") @Nullable Long operationReference,
    @JsonProperty("activateInstructions")
        java.util.@Nullable List<
                GeneratedProcessInstanceModificationActivateInstructionStrictContract>
            activateInstructions,
    @JsonProperty("moveInstructions")
        java.util.@Nullable List<GeneratedProcessInstanceModificationMoveInstructionStrictContract>
            moveInstructions,
    @JsonProperty("terminateInstructions") java.util.@Nullable List<Object> terminateInstructions) {

  public GeneratedProcessInstanceModificationInstructionStrictContract {
    if (operationReference != null)
      if (operationReference < 1L)
        throw new IllegalArgumentException(
            "The value for operationReference is '" + operationReference + "' but must be > 0.");
  }

  public static java.util.List<
          GeneratedProcessInstanceModificationActivateInstructionStrictContract>
      coerceActivateInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "activateInstructions must be a List of GeneratedProcessInstanceModificationActivateInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedProcessInstanceModificationActivateInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof
          GeneratedProcessInstanceModificationActivateInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "activateInstructions must contain only GeneratedProcessInstanceModificationActivateInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static java.util.List<GeneratedProcessInstanceModificationMoveInstructionStrictContract>
      coerceMoveInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "moveInstructions must be a List of GeneratedProcessInstanceModificationMoveInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedProcessInstanceModificationMoveInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedProcessInstanceModificationMoveInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "moveInstructions must contain only GeneratedProcessInstanceModificationMoveInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Long operationReference;
    private Object activateInstructions;
    private Object moveInstructions;
    private java.util.List<Object> terminateInstructions;

    private Builder() {}

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
    public OptionalStep activateInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationActivateInstructionStrictContract>
            activateInstructions) {
      this.activateInstructions = activateInstructions;
      return this;
    }

    @Override
    public OptionalStep activateInstructions(final @Nullable Object activateInstructions) {
      this.activateInstructions = activateInstructions;
      return this;
    }

    public Builder activateInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationActivateInstructionStrictContract>
            activateInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedProcessInstanceModificationActivateInstructionStrictContract>>
            policy) {
      this.activateInstructions =
          policy.apply(activateInstructions, Fields.ACTIVATE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep activateInstructions(
        final @Nullable Object activateInstructions,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.activateInstructions =
          policy.apply(activateInstructions, Fields.ACTIVATE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep moveInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationMoveInstructionStrictContract>
            moveInstructions) {
      this.moveInstructions = moveInstructions;
      return this;
    }

    @Override
    public OptionalStep moveInstructions(final @Nullable Object moveInstructions) {
      this.moveInstructions = moveInstructions;
      return this;
    }

    public Builder moveInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationMoveInstructionStrictContract>
            moveInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceModificationMoveInstructionStrictContract>>
            policy) {
      this.moveInstructions = policy.apply(moveInstructions, Fields.MOVE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep moveInstructions(
        final @Nullable Object moveInstructions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.moveInstructions = policy.apply(moveInstructions, Fields.MOVE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep terminateInstructions(
        final java.util.@Nullable List<Object> terminateInstructions) {
      this.terminateInstructions = terminateInstructions;
      return this;
    }

    @Override
    public OptionalStep terminateInstructions(
        final java.util.@Nullable List<Object> terminateInstructions,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy) {
      this.terminateInstructions =
          policy.apply(terminateInstructions, Fields.TERMINATE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationInstructionStrictContract(
          this.operationReference,
          coerceActivateInstructions(this.activateInstructions),
          coerceMoveInstructions(this.moveInstructions),
          this.terminateInstructions);
    }
  }

  public interface OptionalStep {
    OptionalStep operationReference(final @Nullable Long operationReference);

    OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep activateInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationActivateInstructionStrictContract>
            activateInstructions);

    OptionalStep activateInstructions(final @Nullable Object activateInstructions);

    OptionalStep activateInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationActivateInstructionStrictContract>
            activateInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    GeneratedProcessInstanceModificationActivateInstructionStrictContract>>
            policy);

    OptionalStep activateInstructions(
        final @Nullable Object activateInstructions,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep moveInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationMoveInstructionStrictContract>
            moveInstructions);

    OptionalStep moveInstructions(final @Nullable Object moveInstructions);

    OptionalStep moveInstructions(
        final java.util.@Nullable List<
                GeneratedProcessInstanceModificationMoveInstructionStrictContract>
            moveInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedProcessInstanceModificationMoveInstructionStrictContract>>
            policy);

    OptionalStep moveInstructions(
        final @Nullable Object moveInstructions, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep terminateInstructions(
        final java.util.@Nullable List<Object> terminateInstructions);

    OptionalStep terminateInstructions(
        final java.util.@Nullable List<Object> terminateInstructions,
        final ContractPolicy.FieldPolicy<java.util.List<Object>> policy);

    GeneratedProcessInstanceModificationInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("ProcessInstanceModificationInstruction", "operationReference");
    public static final ContractPolicy.FieldRef ACTIVATE_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceModificationInstruction", "activateInstructions");
    public static final ContractPolicy.FieldRef MOVE_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceModificationInstruction", "moveInstructions");
    public static final ContractPolicy.FieldRef TERMINATE_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceModificationInstruction", "terminateInstructions");

    private Fields() {}
  }
}
