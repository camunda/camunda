/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/variables.yaml#/components/schemas/VariableResultBase
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedVariableResultBaseStrictContract(
    @JsonProperty("name") String name,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("variableKey") String variableKey,
    @JsonProperty("scopeKey") String scopeKey,
    @JsonProperty("processInstanceKey") String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey) {

  public GeneratedVariableResultBaseStrictContract {
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(variableKey, "No variableKey provided.");
    Objects.requireNonNull(scopeKey, "No scopeKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
  }

  public static String coerceVariableKey(final Object value) {
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
        "variableKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceScopeKey(final Object value) {
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
        "scopeKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceProcessInstanceKey(final Object value) {
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
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceRootProcessInstanceKey(final Object value) {
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
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements NameStep,
          TenantIdStep,
          VariableKeyStep,
          ScopeKeyStep,
          ProcessInstanceKeyStep,
          OptionalStep {
    private String name;
    private String tenantId;
    private Object variableKey;
    private Object scopeKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;

    private Builder() {}

    @Override
    public TenantIdStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public VariableKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ScopeKeyStep variableKey(final Object variableKey) {
      this.variableKey = variableKey;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep scopeKey(final Object scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedVariableResultBaseStrictContract build() {
      return new GeneratedVariableResultBaseStrictContract(
          this.name,
          this.tenantId,
          coerceVariableKey(this.variableKey),
          coerceScopeKey(this.scopeKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey));
    }
  }

  public interface NameStep {
    TenantIdStep name(final String name);
  }

  public interface TenantIdStep {
    VariableKeyStep tenantId(final String tenantId);
  }

  public interface VariableKeyStep {
    ScopeKeyStep variableKey(final Object variableKey);
  }

  public interface ScopeKeyStep {
    ProcessInstanceKeyStep scopeKey(final Object scopeKey);
  }

  public interface ProcessInstanceKeyStep {
    OptionalStep processInstanceKey(final Object processInstanceKey);
  }

  public interface OptionalStep {
    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedVariableResultBaseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("VariableResultBase", "name");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("VariableResultBase", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLE_KEY =
        ContractPolicy.field("VariableResultBase", "variableKey");
    public static final ContractPolicy.FieldRef SCOPE_KEY =
        ContractPolicy.field("VariableResultBase", "scopeKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("VariableResultBase", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("VariableResultBase", "rootProcessInstanceKey");

    private Fields() {}
  }
}
