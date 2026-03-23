/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/signals.yaml#/components/schemas/SignalBroadcastResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSignalBroadcastStrictContract(
    @JsonProperty("tenantId") String tenantId, @JsonProperty("signalKey") String signalKey) {

  public GeneratedSignalBroadcastStrictContract {
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(signalKey, "No signalKey provided.");
  }

  public static String coerceSignalKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "signalKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements TenantIdStep, SignalKeyStep, OptionalStep {
    private String tenantId;
    private Object signalKey;

    private Builder() {}

    @Override
    public SignalKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep signalKey(final Object signalKey) {
      this.signalKey = signalKey;
      return this;
    }

    @Override
    public GeneratedSignalBroadcastStrictContract build() {
      return new GeneratedSignalBroadcastStrictContract(
          this.tenantId, coerceSignalKey(this.signalKey));
    }
  }

  public interface TenantIdStep {
    SignalKeyStep tenantId(final String tenantId);
  }

  public interface SignalKeyStep {
    OptionalStep signalKey(final Object signalKey);
  }

  public interface OptionalStep {
    GeneratedSignalBroadcastStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("SignalBroadcastResult", "tenantId");
    public static final ContractPolicy.FieldRef SIGNAL_KEY =
        ContractPolicy.field("SignalBroadcastResult", "signalKey");

    private Fields() {}
  }
}
