/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/EvaluatedDecisionInputItem
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedEvaluatedDecisionInputItemStrictContract(
    String inputId,
    String inputName,
    String inputValue
) {

  public GeneratedEvaluatedDecisionInputItemStrictContract {
    Objects.requireNonNull(inputId, "inputId is required and must not be null");
    Objects.requireNonNull(inputName, "inputName is required and must not be null");
    Objects.requireNonNull(inputValue, "inputValue is required and must not be null");
  }


  public static InputIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements InputIdStep, InputNameStep, InputValueStep, OptionalStep {
    private String inputId;
    private String inputName;
    private String inputValue;

    private Builder() {}

    @Override
    public InputNameStep inputId(final String inputId) {
      this.inputId = inputId;
      return this;
    }

    @Override
    public InputValueStep inputName(final String inputName) {
      this.inputName = inputName;
      return this;
    }

    @Override
    public OptionalStep inputValue(final String inputValue) {
      this.inputValue = inputValue;
      return this;
    }
    @Override
    public GeneratedEvaluatedDecisionInputItemStrictContract build() {
      return new GeneratedEvaluatedDecisionInputItemStrictContract(
          this.inputId,
          this.inputName,
          this.inputValue);
    }
  }

  public interface InputIdStep {
    InputNameStep inputId(final String inputId);
  }

  public interface InputNameStep {
    InputValueStep inputName(final String inputName);
  }

  public interface InputValueStep {
    OptionalStep inputValue(final String inputValue);
  }

  public interface OptionalStep {
    GeneratedEvaluatedDecisionInputItemStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef INPUT_ID = ContractPolicy.field("EvaluatedDecisionInputItem", "inputId");
    public static final ContractPolicy.FieldRef INPUT_NAME = ContractPolicy.field("EvaluatedDecisionInputItem", "inputName");
    public static final ContractPolicy.FieldRef INPUT_VALUE = ContractPolicy.field("EvaluatedDecisionInputItem", "inputValue");

    private Fields() {}
  }


}
