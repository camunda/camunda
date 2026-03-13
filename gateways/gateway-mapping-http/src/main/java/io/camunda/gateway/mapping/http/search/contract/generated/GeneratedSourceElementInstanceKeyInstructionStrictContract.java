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
import org.jspecify.annotations.NullMarked;

@NullMarked
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

  public static SourceTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceTypeStep, SourceElementInstanceKeyStep, OptionalStep {
    private String sourceType;
    private Object sourceElementInstanceKey;

    private Builder() {}

    @Override
    public SourceElementInstanceKeyStep sourceType(final String sourceType) {
      this.sourceType = sourceType;
      return this;
    }

    @Override
    public OptionalStep sourceElementInstanceKey(final Object sourceElementInstanceKey) {
      this.sourceElementInstanceKey = sourceElementInstanceKey;
      return this;
    }

    @Override
    public GeneratedSourceElementInstanceKeyInstructionStrictContract build() {
      return new GeneratedSourceElementInstanceKeyInstructionStrictContract(
          this.sourceType, coerceSourceElementInstanceKey(this.sourceElementInstanceKey));
    }
  }

  public interface SourceTypeStep {
    SourceElementInstanceKeyStep sourceType(final String sourceType);
  }

  public interface SourceElementInstanceKeyStep {
    OptionalStep sourceElementInstanceKey(final Object sourceElementInstanceKey);
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
