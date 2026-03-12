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
public record GeneratedMigrateProcessInstanceMappingInstructionStrictContract(
    String sourceElementId, String targetElementId) {

  public GeneratedMigrateProcessInstanceMappingInstructionStrictContract {
    Objects.requireNonNull(sourceElementId, "sourceElementId is required and must not be null");
    Objects.requireNonNull(targetElementId, "targetElementId is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static SourceElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements SourceElementIdStep, TargetElementIdStep, OptionalStep {
    private String sourceElementId;
    private ContractPolicy.FieldPolicy<String> sourceElementIdPolicy;
    private String targetElementId;
    private ContractPolicy.FieldPolicy<String> targetElementIdPolicy;

    private Builder() {}

    @Override
    public TargetElementIdStep sourceElementId(
        final String sourceElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.sourceElementId = sourceElementId;
      this.sourceElementIdPolicy = policy;
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
    public GeneratedMigrateProcessInstanceMappingInstructionStrictContract build() {
      return new GeneratedMigrateProcessInstanceMappingInstructionStrictContract(
          applyRequiredPolicy(
              this.sourceElementId, this.sourceElementIdPolicy, Fields.SOURCE_ELEMENT_ID),
          applyRequiredPolicy(
              this.targetElementId, this.targetElementIdPolicy, Fields.TARGET_ELEMENT_ID));
    }
  }

  public interface SourceElementIdStep {
    TargetElementIdStep sourceElementId(
        final String sourceElementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TargetElementIdStep {
    OptionalStep targetElementId(
        final String targetElementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedMigrateProcessInstanceMappingInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SOURCE_ELEMENT_ID =
        ContractPolicy.field("MigrateProcessInstanceMappingInstruction", "sourceElementId");
    public static final ContractPolicy.FieldRef TARGET_ELEMENT_ID =
        ContractPolicy.field("MigrateProcessInstanceMappingInstruction", "targetElementId");

    private Fields() {}
  }
}
