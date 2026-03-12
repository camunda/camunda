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
public record GeneratedSourceElementInstanceKeyInstructionStrictContract(
    String sourceType, String sourceElementInstanceKey) {

  public GeneratedSourceElementInstanceKeyInstructionStrictContract {
    Objects.requireNonNull(sourceType, "sourceType is required and must not be null");
    Objects.requireNonNull(
        sourceElementInstanceKey, "sourceElementInstanceKey is required and must not be null");
  }

  public static String coerceSourceElementInstanceKey(final Object value) {
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
        "sourceElementInstanceKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static SourceTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceTypeStep, SourceElementInstanceKeyStep, OptionalStep {
    private String sourceType;
    private ContractPolicy.FieldPolicy<String> sourceTypePolicy;
    private Object sourceElementInstanceKey;
    private ContractPolicy.FieldPolicy<Object> sourceElementInstanceKeyPolicy;

    private Builder() {}

    @Override
    public SourceElementInstanceKeyStep sourceType(
        final String sourceType, final ContractPolicy.FieldPolicy<String> policy) {
      this.sourceType = sourceType;
      this.sourceTypePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep sourceElementInstanceKey(
        final Object sourceElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.sourceElementInstanceKey = sourceElementInstanceKey;
      this.sourceElementInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedSourceElementInstanceKeyInstructionStrictContract build() {
      return new GeneratedSourceElementInstanceKeyInstructionStrictContract(
          applyRequiredPolicy(this.sourceType, this.sourceTypePolicy, Fields.SOURCE_TYPE),
          coerceSourceElementInstanceKey(
              applyRequiredPolicy(
                  this.sourceElementInstanceKey,
                  this.sourceElementInstanceKeyPolicy,
                  Fields.SOURCE_ELEMENT_INSTANCE_KEY)));
    }
  }

  public interface SourceTypeStep {
    SourceElementInstanceKeyStep sourceType(
        final String sourceType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface SourceElementInstanceKeyStep {
    OptionalStep sourceElementInstanceKey(
        final Object sourceElementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedSourceElementInstanceKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_TYPE =
        ContractPolicy.field("SourceElementInstanceKeyInstruction", "sourceType");
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("SourceElementInstanceKeyInstruction", "sourceElementInstanceKey");

    private Fields() {}
  }
}
