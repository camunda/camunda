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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSourceElementIdInstructionStrictContract(
    String sourceType, String sourceElementId) {

  public GeneratedSourceElementIdInstructionStrictContract {
    Objects.requireNonNull(sourceType, "sourceType is required and must not be null");
    Objects.requireNonNull(sourceElementId, "sourceElementId is required and must not be null");
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

  public static final class Builder implements SourceTypeStep, SourceElementIdStep, OptionalStep {
    private String sourceType;
    private ContractPolicy.FieldPolicy<String> sourceTypePolicy;
    private String sourceElementId;
    private ContractPolicy.FieldPolicy<String> sourceElementIdPolicy;

    private Builder() {}

    @Override
    public SourceElementIdStep sourceType(
        final String sourceType, final ContractPolicy.FieldPolicy<String> policy) {
      this.sourceType = sourceType;
      this.sourceTypePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep sourceElementId(
        final String sourceElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.sourceElementId = sourceElementId;
      this.sourceElementIdPolicy = policy;
      return this;
    }

    @Override
    public GeneratedSourceElementIdInstructionStrictContract build() {
      return new GeneratedSourceElementIdInstructionStrictContract(
          applyRequiredPolicy(this.sourceType, this.sourceTypePolicy, Fields.SOURCE_TYPE),
          applyRequiredPolicy(
              this.sourceElementId, this.sourceElementIdPolicy, Fields.SOURCE_ELEMENT_ID));
    }
  }

  public interface SourceTypeStep {
    SourceElementIdStep sourceType(
        final String sourceType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface SourceElementIdStep {
    OptionalStep sourceElementId(
        final String sourceElementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedSourceElementIdInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_TYPE =
        ContractPolicy.field("SourceElementIdInstruction", "sourceType");
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_ID =
        ContractPolicy.field("SourceElementIdInstruction", "sourceElementId");

    private Fields() {}
  }
}
