/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/MessageCorrelationRequest
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
public record GeneratedMessageCorrelationRequestStrictContract(
    String name,
    @Nullable String correlationKey,
    java.util.@Nullable Map<String, Object> variables,
    @Nullable String tenantId
) {

  public GeneratedMessageCorrelationRequestStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
  }


  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, OptionalStep {
    private String name;
    private String correlationKey;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
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
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedMessageCorrelationRequestStrictContract build() {
      return new GeneratedMessageCorrelationRequestStrictContract(
          this.name,
          this.correlationKey,
          this.variables,
          this.tenantId);
    }
  }

  public interface NameStep {
    OptionalStep name(final String name);
  }

  public interface OptionalStep {
  OptionalStep correlationKey(final @Nullable String correlationKey);

  OptionalStep correlationKey(final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables, final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);


  OptionalStep tenantId(final @Nullable String tenantId);

  OptionalStep tenantId(final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedMessageCorrelationRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("MessageCorrelationRequest", "name");
    public static final ContractPolicy.FieldRef CORRELATION_KEY = ContractPolicy.field("MessageCorrelationRequest", "correlationKey");
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("MessageCorrelationRequest", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("MessageCorrelationRequest", "tenantId");

    private Fields() {}
  }


}
