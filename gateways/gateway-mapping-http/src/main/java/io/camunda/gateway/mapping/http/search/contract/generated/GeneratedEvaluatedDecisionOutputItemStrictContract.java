/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/EvaluatedDecisionOutputItem
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluatedDecisionOutputItemStrictContract(
    String outputId,
    String outputName,
    String outputValue,
    @Nullable String ruleId,
    @Nullable Integer ruleIndex
) {

  public GeneratedEvaluatedDecisionOutputItemStrictContract {
    Objects.requireNonNull(outputId, "outputId is required and must not be null");
    Objects.requireNonNull(outputName, "outputName is required and must not be null");
    Objects.requireNonNull(outputValue, "outputValue is required and must not be null");
  }


  public static OutputIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements OutputIdStep, OutputNameStep, OutputValueStep, OptionalStep {
    private String outputId;
    private String outputName;
    private String outputValue;
    private String ruleId;
    private Integer ruleIndex;

    private Builder() {}

    @Override
    public OutputNameStep outputId(final String outputId) {
      this.outputId = outputId;
      return this;
    }

    @Override
    public OutputValueStep outputName(final String outputName) {
      this.outputName = outputName;
      return this;
    }

    @Override
    public OptionalStep outputValue(final String outputValue) {
      this.outputValue = outputValue;
      return this;
    }

    @Override
    public OptionalStep ruleId(final @Nullable String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    @Override
    public OptionalStep ruleId(final @Nullable String ruleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ruleId = policy.apply(ruleId, Fields.RULE_ID, null);
      return this;
    }


    @Override
    public OptionalStep ruleIndex(final @Nullable Integer ruleIndex) {
      this.ruleIndex = ruleIndex;
      return this;
    }

    @Override
    public OptionalStep ruleIndex(final @Nullable Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.ruleIndex = policy.apply(ruleIndex, Fields.RULE_INDEX, null);
      return this;
    }

    @Override
    public GeneratedEvaluatedDecisionOutputItemStrictContract build() {
      return new GeneratedEvaluatedDecisionOutputItemStrictContract(
          this.outputId,
          this.outputName,
          this.outputValue,
          this.ruleId,
          this.ruleIndex);
    }
  }

  public interface OutputIdStep {
    OutputNameStep outputId(final String outputId);
  }

  public interface OutputNameStep {
    OutputValueStep outputName(final String outputName);
  }

  public interface OutputValueStep {
    OptionalStep outputValue(final String outputValue);
  }

  public interface OptionalStep {
  OptionalStep ruleId(final @Nullable String ruleId);

  OptionalStep ruleId(final @Nullable String ruleId, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep ruleIndex(final @Nullable Integer ruleIndex);

  OptionalStep ruleIndex(final @Nullable Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy);


    GeneratedEvaluatedDecisionOutputItemStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef OUTPUT_ID = ContractPolicy.field("EvaluatedDecisionOutputItem", "outputId");
    public static final ContractPolicy.FieldRef OUTPUT_NAME = ContractPolicy.field("EvaluatedDecisionOutputItem", "outputName");
    public static final ContractPolicy.FieldRef OUTPUT_VALUE = ContractPolicy.field("EvaluatedDecisionOutputItem", "outputValue");
    public static final ContractPolicy.FieldRef RULE_ID = ContractPolicy.field("EvaluatedDecisionOutputItem", "ruleId");
    public static final ContractPolicy.FieldRef RULE_INDEX = ContractPolicy.field("EvaluatedDecisionOutputItem", "ruleIndex");

    private Fields() {}
  }


}
