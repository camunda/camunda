/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/MatchedDecisionRuleItem
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMatchedDecisionRuleItemStrictContract(
    String ruleId,
    Integer ruleIndex,
    java.util.List<GeneratedEvaluatedDecisionOutputItemStrictContract> evaluatedOutputs
) {

  public GeneratedMatchedDecisionRuleItemStrictContract {
    Objects.requireNonNull(ruleId, "ruleId is required and must not be null");
    Objects.requireNonNull(ruleIndex, "ruleIndex is required and must not be null");
    Objects.requireNonNull(evaluatedOutputs, "evaluatedOutputs is required and must not be null");
  }

  public static java.util.List<GeneratedEvaluatedDecisionOutputItemStrictContract> coerceEvaluatedOutputs(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "evaluatedOutputs must be a List of GeneratedEvaluatedDecisionOutputItemStrictContract, but was " + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedEvaluatedDecisionOutputItemStrictContract>(listValue.size());
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



  public static RuleIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements RuleIdStep, RuleIndexStep, EvaluatedOutputsStep, OptionalStep {
    private String ruleId;
    private Integer ruleIndex;
    private Object evaluatedOutputs;

    private Builder() {}

    @Override
    public RuleIndexStep ruleId(final String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    @Override
    public EvaluatedOutputsStep ruleIndex(final Integer ruleIndex) {
      this.ruleIndex = ruleIndex;
      return this;
    }

    @Override
    public OptionalStep evaluatedOutputs(final Object evaluatedOutputs) {
      this.evaluatedOutputs = evaluatedOutputs;
      return this;
    }
    @Override
    public GeneratedMatchedDecisionRuleItemStrictContract build() {
      return new GeneratedMatchedDecisionRuleItemStrictContract(
          this.ruleId,
          this.ruleIndex,
          coerceEvaluatedOutputs(this.evaluatedOutputs));
    }
  }

  public interface RuleIdStep {
    RuleIndexStep ruleId(final String ruleId);
  }

  public interface RuleIndexStep {
    EvaluatedOutputsStep ruleIndex(final Integer ruleIndex);
  }

  public interface EvaluatedOutputsStep {
    OptionalStep evaluatedOutputs(final Object evaluatedOutputs);
  }

  public interface OptionalStep {
    GeneratedMatchedDecisionRuleItemStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef RULE_ID = ContractPolicy.field("MatchedDecisionRuleItem", "ruleId");
    public static final ContractPolicy.FieldRef RULE_INDEX = ContractPolicy.field("MatchedDecisionRuleItem", "ruleIndex");
    public static final ContractPolicy.FieldRef EVALUATED_OUTPUTS = ContractPolicy.field("MatchedDecisionRuleItem", "evaluatedOutputs");

    private Fields() {}
  }


}
