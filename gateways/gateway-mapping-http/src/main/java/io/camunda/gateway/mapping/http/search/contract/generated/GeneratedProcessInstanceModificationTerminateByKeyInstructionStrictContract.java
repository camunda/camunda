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
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract(
    String elementInstanceKey) {

  public GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract {
    Objects.requireNonNull(
        elementInstanceKey, "elementInstanceKey is required and must not be null");
  }

  public static String coerceElementInstanceKey(final Object value) {
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
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ElementInstanceKeyStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementInstanceKeyStep, OptionalStep {
    private Object elementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> elementInstanceKeyPolicy;

    private Builder() {}

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = elementInstanceKey;
      this.elementInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract build() {
      return new GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract(
          coerceElementInstanceKey(
              applyRequiredPolicy(
                  this.elementInstanceKey,
                  this.elementInstanceKeyPolicy,
                  Fields.ELEMENT_INSTANCE_KEY)));
    }
  }

  public interface ElementInstanceKeyStep {
    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field(
            "ProcessInstanceModificationTerminateByKeyInstruction", "elementInstanceKey");

    private Fields() {}
  }
}
