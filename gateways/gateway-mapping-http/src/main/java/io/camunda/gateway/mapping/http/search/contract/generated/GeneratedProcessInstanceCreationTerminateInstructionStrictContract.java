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
public record GeneratedProcessInstanceCreationTerminateInstructionStrictContract(
    @Nullable String type, String afterElementId) {

  public GeneratedProcessInstanceCreationTerminateInstructionStrictContract {
    Objects.requireNonNull(afterElementId, "afterElementId is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static AfterElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements AfterElementIdStep, OptionalStep {
    private String type;
    private String afterElementId;
    private ContractPolicy.FieldPolicy<String> afterElementIdPolicy;

    private Builder() {}

    @Override
    public OptionalStep afterElementId(
        final String afterElementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.afterElementId = afterElementId;
      this.afterElementIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceCreationTerminateInstructionStrictContract build() {
      return new GeneratedProcessInstanceCreationTerminateInstructionStrictContract(
          this.type,
          applyRequiredPolicy(
              this.afterElementId, this.afterElementIdPolicy, Fields.AFTER_ELEMENT_ID));
    }
  }

  public interface AfterElementIdStep {
    OptionalStep afterElementId(
        final String afterElementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep type(final String type);

    OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessInstanceCreationTerminateInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ProcessInstanceCreationTerminateInstruction", "type");
    public static final ContractPolicy.FieldRef AFTER_ELEMENT_ID =
        ContractPolicy.field("ProcessInstanceCreationTerminateInstruction", "afterElementId");

    private Fields() {}
  }
}
