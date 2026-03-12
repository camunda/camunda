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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMatchedDecisionRuleItemStrictContract(
    String ruleId,
    Integer ruleIndex,
    java.util.List<GeneratedEvaluatedDecisionOutputItemStrictContract> evaluatedOutputs) {

  public GeneratedMatchedDecisionRuleItemStrictContract {
    Objects.requireNonNull(ruleId, "ruleId is required and must not be null");
    Objects.requireNonNull(ruleIndex, "ruleIndex is required and must not be null");
    Objects.requireNonNull(evaluatedOutputs, "evaluatedOutputs is required and must not be null");
  }

  public static java.util.List<GeneratedEvaluatedDecisionOutputItemStrictContract>
      coerceEvaluatedOutputs(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "evaluatedOutputs must be a List of GeneratedEvaluatedDecisionOutputItemStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedEvaluatedDecisionOutputItemStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedEvaluatedDecisionOutputItemStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "evaluatedOutputs must contain only GeneratedEvaluatedDecisionOutputItemStrictContract items, but got "
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

  public static RuleIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements RuleIdStep, RuleIndexStep, EvaluatedOutputsStep, OptionalStep {
    private String ruleId;
    private ContractPolicy.FieldPolicy<String> ruleIdPolicy;
    private Integer ruleIndex;
    private ContractPolicy.FieldPolicy<Integer> ruleIndexPolicy;
    private Object evaluatedOutputs;
    private ContractPolicy.FieldPolicy<Object> evaluatedOutputsPolicy;

    private Builder() {}

    @Override
    public RuleIndexStep ruleId(
        final String ruleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ruleId = ruleId;
      this.ruleIdPolicy = policy;
      return this;
    }

    @Override
    public EvaluatedOutputsStep ruleIndex(
        final Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.ruleIndex = ruleIndex;
      this.ruleIndexPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep evaluatedOutputs(
        final Object evaluatedOutputs, final ContractPolicy.FieldPolicy<Object> policy) {
      this.evaluatedOutputs = evaluatedOutputs;
      this.evaluatedOutputsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedMatchedDecisionRuleItemStrictContract build() {
      return new GeneratedMatchedDecisionRuleItemStrictContract(
          applyRequiredPolicy(this.ruleId, this.ruleIdPolicy, Fields.RULE_ID),
          applyRequiredPolicy(this.ruleIndex, this.ruleIndexPolicy, Fields.RULE_INDEX),
          coerceEvaluatedOutputs(
              applyRequiredPolicy(
                  this.evaluatedOutputs, this.evaluatedOutputsPolicy, Fields.EVALUATED_OUTPUTS)));
    }
  }

  public interface RuleIdStep {
    RuleIndexStep ruleId(final String ruleId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface RuleIndexStep {
    EvaluatedOutputsStep ruleIndex(
        final Integer ruleIndex, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface EvaluatedOutputsStep {
    OptionalStep evaluatedOutputs(
        final Object evaluatedOutputs, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedMatchedDecisionRuleItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef RULE_ID =
        ContractPolicy.field("MatchedDecisionRuleItem", "ruleId");
    public static final ContractPolicy.FieldRef RULE_INDEX =
        ContractPolicy.field("MatchedDecisionRuleItem", "ruleIndex");
    public static final ContractPolicy.FieldRef EVALUATED_OUTPUTS =
        ContractPolicy.field("MatchedDecisionRuleItem", "evaluatedOutputs");

    private Fields() {}
  }
}
