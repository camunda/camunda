/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationActivateInstructionStrictContract(
    String elementId,
    @Nullable
        java.util.List<GeneratedModifyProcessInstanceVariableInstructionStrictContract>
            variableInstructions,
    @Nullable String ancestorElementInstanceKey) {

  public GeneratedProcessInstanceModificationActivateInstructionStrictContract {
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
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

  public static String coerceAncestorElementInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "ancestorElementInstanceKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementIdStep, OptionalStep {
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private Object variableInstructions;
    private Object ancestorElementInstanceKey;

    private Builder() {}

    @Override
    public OptionalStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
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
    public OptionalStep ancestorElementInstanceKey(final String ancestorElementInstanceKey) {
      this.ancestorElementInstanceKey = ancestorElementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep ancestorElementInstanceKey(final Object ancestorElementInstanceKey) {
      this.ancestorElementInstanceKey = ancestorElementInstanceKey;
      return this;
    }

    public Builder ancestorElementInstanceKey(
        final String ancestorElementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.ancestorElementInstanceKey =
          policy.apply(ancestorElementInstanceKey, Fields.ANCESTOR_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep ancestorElementInstanceKey(
        final Object ancestorElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.ancestorElementInstanceKey =
          policy.apply(ancestorElementInstanceKey, Fields.ANCESTOR_ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationActivateInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationActivateInstructionStrictContract(
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          coerceVariableInstructions(this.variableInstructions),
          coerceAncestorElementInstanceKey(this.ancestorElementInstanceKey));
    }
  }

  public interface ElementIdStep {
    OptionalStep elementId(final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
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

    OptionalStep ancestorElementInstanceKey(final String ancestorElementInstanceKey);

    OptionalStep ancestorElementInstanceKey(final Object ancestorElementInstanceKey);

    OptionalStep ancestorElementInstanceKey(
        final String ancestorElementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep ancestorElementInstanceKey(
        final Object ancestorElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessInstanceModificationActivateInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceModificationActivateInstruction", "elementId");
    public static final ContractPolicy.FieldRef VARIABLE_INSTRUCTIONS =
        ContractPolicy.field(
            "ProcessInstanceModificationActivateInstruction", "variableInstructions");
    public static final ContractPolicy.FieldRef ANCESTOR_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field(
            "ProcessInstanceModificationActivateInstruction", "ancestorElementInstanceKey");

    private Fields() {}
  }
}
