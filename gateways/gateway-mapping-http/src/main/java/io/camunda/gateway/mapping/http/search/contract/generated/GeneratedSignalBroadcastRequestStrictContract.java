/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/signals.yaml#/components/schemas/SignalBroadcastRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSignalBroadcastRequestStrictContract(
    @Nullable String signalName,
    java.util.@Nullable Map<String, Object> variables,
    @Nullable String tenantId
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String signalName;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep signalName(final @Nullable String signalName) {
      this.signalName = signalName;
      return this;
    }

    @Override
    public OptionalStep signalName(final @Nullable String signalName, final ContractPolicy.FieldPolicy<String> policy) {
      this.signalName = policy.apply(signalName, Fields.SIGNAL_NAME, null);
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
    public GeneratedSignalBroadcastRequestStrictContract build() {
      return new GeneratedSignalBroadcastRequestStrictContract(
          this.signalName,
          this.variables,
          this.tenantId);
    }
  }

  public interface OptionalStep {
  OptionalStep signalName(final @Nullable String signalName);

  OptionalStep signalName(final @Nullable String signalName, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

  OptionalStep variables(final java.util.@Nullable Map<String, Object> variables, final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);


  OptionalStep tenantId(final @Nullable String tenantId);

  OptionalStep tenantId(final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedSignalBroadcastRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef SIGNAL_NAME = ContractPolicy.field("SignalBroadcastRequest", "signalName");
    public static final ContractPolicy.FieldRef VARIABLES = ContractPolicy.field("SignalBroadcastRequest", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("SignalBroadcastRequest", "tenantId");

    private Fields() {}
  }


}
