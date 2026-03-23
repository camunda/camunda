/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/expression.yaml#/components/schemas/ExpressionEvaluationResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedExpressionEvaluationStrictContract(
    @JsonProperty("expression") String expression,
    @JsonProperty("result") Object result,
    @JsonProperty("warnings") java.util.List<String> warnings) {

  public GeneratedExpressionEvaluationStrictContract {
    Objects.requireNonNull(expression, "No expression provided.");
    Objects.requireNonNull(result, "No result provided.");
    Objects.requireNonNull(warnings, "No warnings provided.");
  }

  public static ExpressionStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ExpressionStep, ResultStep, WarningsStep, OptionalStep {
    private String expression;
    private Object result;
    private java.util.List<String> warnings;

    private Builder() {}

    @Override
    public ResultStep expression(final String expression) {
      this.expression = expression;
      return this;
    }

    @Override
    public WarningsStep result(final Object result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep warnings(final java.util.List<String> warnings) {
      this.warnings = warnings;
      return this;
    }

    @Override
    public GeneratedExpressionEvaluationStrictContract build() {
      return new GeneratedExpressionEvaluationStrictContract(
          this.expression, this.result, this.warnings);
    }
  }

  public interface ExpressionStep {
    ResultStep expression(final String expression);
  }

  public interface ResultStep {
    WarningsStep result(final Object result);
  }

  public interface WarningsStep {
    OptionalStep warnings(final java.util.List<String> warnings);
  }

  public interface OptionalStep {
    GeneratedExpressionEvaluationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EXPRESSION =
        ContractPolicy.field("ExpressionEvaluationResult", "expression");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("ExpressionEvaluationResult", "result");
    public static final ContractPolicy.FieldRef WARNINGS =
        ContractPolicy.field("ExpressionEvaluationResult", "warnings");

    private Fields() {}
  }
}
