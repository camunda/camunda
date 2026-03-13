/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/element-instances.yaml#/components/schemas/AdHocSubProcessActivateActivityReference
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
public record GeneratedAdHocSubProcessActivateActivityReferenceStrictContract(
    String elementId,
    java.util.@Nullable Map<String, Object> variables
) {

  public GeneratedAdHocSubProcessActivateActivityReferenceStrictContract {
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
  }


  public static ElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ElementIdStep, OptionalStep {
    private String elementId;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables, final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public GeneratedAdHocSubProcessActivateActivityReferenceStrictContract build() {
      return new GeneratedAdHocSubProcessActivateActivityReferenceStrictContract(
          this.elementId,
          this.variables);
    }
  }

  public interface ElementIdStep {
    OptionalStep elementId(final String elementId);
  }

  public interface OptionalStep {
  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables, final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);


    GeneratedAdHocSubProcessActivateActivityReferenceStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_ID = ContractPolicy.field("AdHocSubProcessActivateActivityReference", "elementId");
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("AdHocSubProcessActivateActivityReference", "variables");

    private Fields() {}
  }


}
