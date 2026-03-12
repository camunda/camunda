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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluatedDecisionOutputItemStrictContract(
    String outputId,
    String outputName,
    String outputValue,
    @Nullable String ruleId,
    @Nullable Integer ruleIndex) {

  public GeneratedEvaluatedDecisionOutputItemStrictContract {
    Objects.requireNonNull(outputId, "outputId is required and must not be null");
    Objects.requireNonNull(outputName, "outputName is required and must not be null");
    Objects.requireNonNull(outputValue, "outputValue is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OutputIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements OutputIdStep, OutputNameStep, OutputValueStep, OptionalStep {
    private String outputId;
    private ContractPolicy.FieldPolicy<String> outputIdPolicy;
    private String outputName;
    private ContractPolicy.FieldPolicy<String> outputNamePolicy;
    private String outputValue;
    private ContractPolicy.FieldPolicy<String> outputValuePolicy;
    private String ruleId;
    private Integer ruleIndex;

    private Builder() {}

    @Override
    public OutputNameStep outputId(
        final String outputId, final ContractPolicy.FieldPolicy<String> policy) {
      this.outputId = outputId;
      this.outputIdPolicy = policy;
      return this;
    }

    @Override
    public OutputValueStep outputName(
        final String outputName, final ContractPolicy.FieldPolicy<String> policy) {
      this.outputName = outputName;
      this.outputNamePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep outputValue(
        final String outputValue, final ContractPolicy.FieldPolicy<String> policy) {
      this.outputValue = outputValue;
      this.outputValuePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep ruleId(final String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    @Override
    public OptionalStep ruleId(
        final String ruleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ruleId = policy.apply(ruleId, Fields.RULE_ID, null);
      return this;
    }

    @Override
    public OptionalStep ruleIndex(final Integer ruleIndex) {
      this.ruleIndex = ruleIndex;
      return this;
    }

    @Override
    public OptionalStep ruleIndex(
        final Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.ruleIndex = policy.apply(ruleIndex, Fields.RULE_INDEX, null);
      return this;
    }

    @Override
    public GeneratedEvaluatedDecisionOutputItemStrictContract build() {
      return new GeneratedEvaluatedDecisionOutputItemStrictContract(
          applyRequiredPolicy(this.outputId, this.outputIdPolicy, Fields.OUTPUT_ID),
          applyRequiredPolicy(this.outputName, this.outputNamePolicy, Fields.OUTPUT_NAME),
          applyRequiredPolicy(this.outputValue, this.outputValuePolicy, Fields.OUTPUT_VALUE),
          this.ruleId,
          this.ruleIndex);
    }
  }

  public interface OutputIdStep {
    OutputNameStep outputId(final String outputId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OutputNameStep {
    OutputValueStep outputName(
        final String outputName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OutputValueStep {
    OptionalStep outputValue(
        final String outputValue, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep ruleId(final String ruleId);

    OptionalStep ruleId(final String ruleId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep ruleIndex(final Integer ruleIndex);

    OptionalStep ruleIndex(
        final Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedEvaluatedDecisionOutputItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OUTPUT_ID =
        ContractPolicy.field("EvaluatedDecisionOutputItem", "outputId");
    public static final ContractPolicy.FieldRef OUTPUT_NAME =
        ContractPolicy.field("EvaluatedDecisionOutputItem", "outputName");
    public static final ContractPolicy.FieldRef OUTPUT_VALUE =
        ContractPolicy.field("EvaluatedDecisionOutputItem", "outputValue");
    public static final ContractPolicy.FieldRef RULE_ID =
        ContractPolicy.field("EvaluatedDecisionOutputItem", "ruleId");
    public static final ContractPolicy.FieldRef RULE_INDEX =
        ContractPolicy.field("EvaluatedDecisionOutputItem", "ruleIndex");

    private Fields() {}
  }
}
