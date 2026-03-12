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
public record GeneratedProcessInstanceModificationMoveInstructionStrictContract(
    Object sourceElementInstruction,
    String targetElementId,
    @Nullable Object ancestorScopeInstruction,
    @Nullable
        java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions) {

  public GeneratedProcessInstanceModificationMoveInstructionStrictContract {
    Objects.requireNonNull(
        sourceElementInstruction, "sourceElementInstruction is required and must not be null");
    Objects.requireNonNull(targetElementId, "targetElementId is required and must not be null");
  }

  public static java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
      coerceVariableInstructions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "variableInstructions must be a List of GeneratedModifyProcessInstanceVariableInstructionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedModifyProcessInstanceVariableInstructionStrictContract>(
            listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item
          instanceof GeneratedModifyProcessInstanceVariableInstructionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "variableInstructions must contain only GeneratedModifyProcessInstanceVariableInstructionStrictContract items, but got "
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

  public static SourceElementInstructionStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceElementInstructionStep, TargetElementIdStep, OptionalStep {
    private Object sourceElementInstruction;
    private ContractPolicy.FieldPolicy<Object> sourceElementInstructionPolicy;
    private String targetElementId;
    private ContractPolicy.FieldPolicy<String> targetElementIdPolicy;
    private Object ancestorScopeInstruction;
    private Object variableInstructions;

    private Builder() {}

    @Override
    public TargetElementIdStep sourceElementInstruction(
        final Object sourceElementInstruction, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sourceElementInstruction = sourceElementInstruction;
      this.sourceElementInstructionPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep targetElementId(
        final String targetElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.targetElementId = targetElementId;
      this.targetElementIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep ancestorScopeInstruction(final Object ancestorScopeInstruction) {
      this.ancestorScopeInstruction = ancestorScopeInstruction;
      return this;
    }

    @Override
    public OptionalStep ancestorScopeInstruction(
        final Object ancestorScopeInstruction, final ContractPolicy.FieldPolicy<Object> policy) {
      this.ancestorScopeInstruction =
          policy.apply(ancestorScopeInstruction, Fields.ANCESTOR_SCOPE_INSTRUCTION, null);
      return this;
    }

    @Override
    public OptionalStep variableInstructions(
        final java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions) {
      this.variableInstructions = variableInstructions;
      return this;
    }

    @Override
    public OptionalStep variableInstructions(final Object variableInstructions) {
      this.variableInstructions = variableInstructions;
      return this;
    }

    public Builder variableInstructions(
        final java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>>
            policy) {
      this.variableInstructions =
          policy.apply(variableInstructions, Fields.VARIABLE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public OptionalStep variableInstructions(
        final Object variableInstructions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variableInstructions =
          policy.apply(variableInstructions, Fields.VARIABLE_INSTRUCTIONS, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationMoveInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationMoveInstructionStrictContract(
          applyRequiredPolicy(
              this.sourceElementInstruction,
              this.sourceElementInstructionPolicy,
              Fields.SOURCE_ELEMENT_INSTRUCTION),
          applyRequiredPolicy(
              this.targetElementId, this.targetElementIdPolicy, Fields.TARGET_ELEMENT_ID),
          this.ancestorScopeInstruction,
          coerceVariableInstructions(this.variableInstructions));
    }
  }

  public interface SourceElementInstructionStep {
    TargetElementIdStep sourceElementInstruction(
        final Object sourceElementInstruction, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TargetElementIdStep {
    OptionalStep targetElementId(
        final String targetElementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep ancestorScopeInstruction(final Object ancestorScopeInstruction);

    OptionalStep ancestorScopeInstruction(
        final Object ancestorScopeInstruction, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep variableInstructions(
        final java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions);

    OptionalStep variableInstructions(final Object variableInstructions);

    OptionalStep variableInstructions(
        final java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>>
            policy);

    OptionalStep variableInstructions(
        final Object variableInstructions, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessInstanceModificationMoveInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_INSTRUCTION =
        ContractPolicy.field(
            "ProcessInstanceModificationMoveInstruction", "sourceElementInstruction");
    public static final ContractPolicy.FieldRef TARGET_ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "targetElementId");
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_INSTRUCTION =
        ContractPolicy.field(
            "ProcessInstanceModificationMoveInstruction", "ancestorScopeInstruction");
    public static final ContractPolicy.FieldRef VARIABLE_INSTRUCTIONS =
        ContractPolicy.field("ProcessInstanceModificationMoveInstruction", "variableInstructions");

    private Fields() {}
  }
}
