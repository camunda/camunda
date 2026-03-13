/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceModificationMoveInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationMoveInstructionStrictContract(
    Object sourceElementInstruction,
    String targetElementId,
    @Nullable Object ancestorScopeInstruction,
    java.util.@Nullable List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> variableInstructions
) {

  public GeneratedProcessInstanceModificationMoveInstructionStrictContract {
    Objects.requireNonNull(sourceElementInstruction, "sourceElementInstruction is required and must not be null");
    Objects.requireNonNull(targetElementId, "targetElementId is required and must not be null");
  }

  public static java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> coerceVariableInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "variableInstructions must be a List of GeneratedModifyProcessInstanceVariableInstructionStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedModifyProcessInstanceVariableInstructionStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedModifyProcessInstanceVariableInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "variableInstructions must contain only GeneratedModifyProcessInstanceVariableInstructionStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }



  public static SourceElementInstructionStep builder() {
    return new Builder();
  }

  public static final class Builder implements SourceElementInstructionStep, TargetElementIdStep, OptionalStep {
    private Object sourceElementInstruction;
    private String targetElementId;
    private Object ancestorScopeInstruction;
    private Object variableInstructions;

    private Builder() {}

    @Override
    public TargetElementIdStep sourceElementInstruction(final Object sourceElementInstruction) {
      this.sourceElementInstruction = sourceElementInstruction;
      return this;
    }

    @Override
    public OptionalStep targetElementId(final String targetElementId) {
      this.targetElementId = targetElementId;
      return this;
    }

    @Override
    public OptionalStep ancestorScopeInstruction(final @Nullable Object ancestorScopeInstruction) {
      this.ancestorScopeInstruction = ancestorScopeInstruction;
      return this;
    }

    @Override
    public OptionalStep ancestorScopeInstruction(final @Nullable Object ancestorScopeInstruction, final ContractPolicy.FieldPolicy<Object> policy) {
      this.ancestorScopeInstruction = policy.apply(ancestorScopeInstruction, Fields.ANCESTOR_SCOPE_INSTRUCTION, null);
      return this;
    }


    @Override
    public OptionalStep variableInstructions(final java.util.@Nullable List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> variableInstructions) {
      this.variableInstructions = variableInstructions;
      return this;
    }

    @Override
    public OptionalStep variableInstructions(final @Nullable Object variableInstructions) {
      this.variableInstructions = variableInstructions;
      return this;
    }

    public Builder variableInstructions(final java.util.@Nullable List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> variableInstructions, final ContractPolicy.FieldPolicy<java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>> policy) {
      this.variableInstructions = policy.apply(variableInstructions, Fields.VARIABLE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep variableInstructions(final @Nullable Object variableInstructions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variableInstructions = policy.apply(variableInstructions, Fields.VARIABLE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationMoveInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationMoveInstructionStrictContract(
          this.sourceElementInstruction,
          this.targetElementId,
          this.ancestorScopeInstruction,
          coerceVariableInstructions(this.variableInstructions));
    }
  }

  public interface SourceElementInstructionStep {
    TargetElementIdStep sourceElementInstruction(final Object sourceElementInstruction);
  }

  public interface TargetElementIdStep {
    OptionalStep targetElementId(final String targetElementId);
  }

  public interface OptionalStep {
  OptionalStep ancestorScopeInstruction(final @Nullable Object ancestorScopeInstruction);

  OptionalStep ancestorScopeInstruction(final @Nullable Object ancestorScopeInstruction, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep variableInstructions(final java.util.@Nullable List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> variableInstructions);

  OptionalStep variableInstructions(final @Nullable Object variableInstructions);

  OptionalStep variableInstructions(final java.util.@Nullable List<GeneratedModifyProcessInstanceVariableInstructionStrictContract> variableInstructions, final ContractPolicy.FieldPolicy<java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>> policy);

  OptionalStep variableInstructions(final @Nullable Object variableInstructions, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedProcessInstanceModificationMoveInstructionStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_INSTRUCTION = ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "sourceElementInstruction");
    public static final ContractPolicy.FieldRef TARGET_ELEMENT_ID = ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "targetElementId");
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_INSTRUCTION = ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "ancestorScopeInstruction");
    public static final ContractPolicy.FieldRef VARIABLE_INSTRUCTIONS = ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "variableInstructions");

    private Fields() {}
  }


}
