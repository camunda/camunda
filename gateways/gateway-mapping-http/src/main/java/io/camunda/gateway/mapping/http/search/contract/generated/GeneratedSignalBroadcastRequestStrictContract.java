/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSignalBroadcastRequestStrictContract(
    @JsonProperty("signalName") String signalName,
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables,
    @JsonProperty("tenantId") @Nullable String tenantId) {

  public GeneratedSignalBroadcastRequestStrictContract {
    Objects.requireNonNull(signalName, "No signalName provided.");
    if (tenantId != null)
      if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    if (tenantId != null)
      if (tenantId.length() > 256)
        throw new IllegalArgumentException(
            "The provided tenantId exceeds the limit of 256 characters.");
    if (tenantId != null)
      if (!tenantId.matches("^(<default>|[A-Za-z0-9_@.+-]+)$"))
        throw new IllegalArgumentException(
            "The provided tenantId contains illegal characters. It must match the pattern '^(<default>|[A-Za-z0-9_@.+-]+)$'.");
  }

  public static SignalNameStep builder() {
    return new Builder();
  }

  public static final class Builder implements SignalNameStep, OptionalStep {
    private String signalName;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep signalName(final String signalName) {
      this.signalName = signalName;
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedSignalBroadcastRequestStrictContract build() {
      return new GeneratedSignalBroadcastRequestStrictContract(
          this.signalName, this.variables, this.tenantId);
    }
  }

  public interface SignalNameStep {
    OptionalStep signalName(final String signalName);
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedSignalBroadcastRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef SIGNAL_NAME =
        ContractPolicy.field("SignalBroadcastRequest", "signalName");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("SignalBroadcastRequest", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("SignalBroadcastRequest", "tenantId");

    private Fields() {}
  }
}
