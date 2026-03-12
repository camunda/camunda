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
public record GeneratedEvaluatedDecisionInputItemStrictContract(
    String inputId, String inputName, String inputValue) {

  public GeneratedEvaluatedDecisionInputItemStrictContract {
    Objects.requireNonNull(inputId, "inputId is required and must not be null");
    Objects.requireNonNull(inputName, "inputName is required and must not be null");
    Objects.requireNonNull(inputValue, "inputValue is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static InputIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements InputIdStep, InputNameStep, InputValueStep, OptionalStep {
    private String inputId;
    private ContractPolicy.FieldPolicy<String> inputIdPolicy;
    private String inputName;
    private ContractPolicy.FieldPolicy<String> inputNamePolicy;
    private String inputValue;
    private ContractPolicy.FieldPolicy<String> inputValuePolicy;

    private Builder() {}

    @Override
    public InputNameStep inputId(
        final String inputId, final ContractPolicy.FieldPolicy<String> policy) {
      this.inputId = inputId;
      this.inputIdPolicy = policy;
      return this;
    }

    @Override
    public InputValueStep inputName(
        final String inputName, final ContractPolicy.FieldPolicy<String> policy) {
      this.inputName = inputName;
      this.inputNamePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep inputValue(
        final String inputValue, final ContractPolicy.FieldPolicy<String> policy) {
      this.inputValue = inputValue;
      this.inputValuePolicy = policy;
      return this;
    }

    @Override
    public GeneratedEvaluatedDecisionInputItemStrictContract build() {
      return new GeneratedEvaluatedDecisionInputItemStrictContract(
          applyRequiredPolicy(this.inputId, this.inputIdPolicy, Fields.INPUT_ID),
          applyRequiredPolicy(this.inputName, this.inputNamePolicy, Fields.INPUT_NAME),
          applyRequiredPolicy(this.inputValue, this.inputValuePolicy, Fields.INPUT_VALUE));
    }
  }

  public interface InputIdStep {
    InputNameStep inputId(final String inputId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface InputNameStep {
    InputValueStep inputName(
        final String inputName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface InputValueStep {
    OptionalStep inputValue(
        final String inputValue, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedEvaluatedDecisionInputItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef INPUT_ID =
        ContractPolicy.field("EvaluatedDecisionInputItem", "inputId");
    public static final ContractPolicy.FieldRef INPUT_NAME =
        ContractPolicy.field("EvaluatedDecisionInputItem", "inputName");
    public static final ContractPolicy.FieldRef INPUT_VALUE =
        ContractPolicy.field("EvaluatedDecisionInputItem", "inputValue");

    private Fields() {}
  }
}
