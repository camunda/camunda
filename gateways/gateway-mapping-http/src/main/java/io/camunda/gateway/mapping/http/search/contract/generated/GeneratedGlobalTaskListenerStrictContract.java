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
public record GeneratedGlobalTaskListenerStrictContract(
    String id, io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source) {

  public GeneratedGlobalTaskListenerStrictContract {
    Objects.requireNonNull(id, "id is required and must not be null");
    Objects.requireNonNull(source, "source is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static IdStep builder() {
    return new Builder();
  }

  public static final class Builder implements IdStep, SourceStep, OptionalStep {
    private String id;
    private ContractPolicy.FieldPolicy<String> idPolicy;
    private io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.GlobalListenerSourceEnum>
        sourcePolicy;

    private Builder() {}

    @Override
    public SourceStep id(final String id, final ContractPolicy.FieldPolicy<String> policy) {
      this.id = id;
      this.idPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep source(
        final io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.GlobalListenerSourceEnum>
            policy) {
      this.source = source;
      this.sourcePolicy = policy;
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerStrictContract build() {
      return new GeneratedGlobalTaskListenerStrictContract(
          applyRequiredPolicy(this.id, this.idPolicy, Fields.ID),
          applyRequiredPolicy(this.source, this.sourcePolicy, Fields.SOURCE));
    }
  }

  public interface IdStep {
    SourceStep id(final String id, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface SourceStep {
    OptionalStep source(
        final io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.GlobalListenerSourceEnum>
            policy);
  }

  public interface OptionalStep {
    GeneratedGlobalTaskListenerStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ID =
        ContractPolicy.field("GlobalTaskListenerResult", "id");
    public static final ContractPolicy.FieldRef SOURCE =
        ContractPolicy.field("GlobalTaskListenerResult", "source");

    private Fields() {}
  }
}
