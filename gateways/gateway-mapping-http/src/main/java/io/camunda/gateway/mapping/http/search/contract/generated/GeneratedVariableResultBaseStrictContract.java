/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedVariableResultBaseStrictContract(
    String name,
    String tenantId,
    String variableKey,
    String scopeKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey) {

  public GeneratedVariableResultBaseStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(variableKey, "variableKey is required and must not be null");
    Objects.requireNonNull(scopeKey, "scopeKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object variableKey;
    private ContractPolicy.FieldPolicy<Object> variableKeyPolicy;
    private Object scopeKey;
    private ContractPolicy.FieldPolicy<Object> scopeKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;

    private Builder() {}

    @Override
    public TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public VariableKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ScopeKeyStep variableKey(
        final Object variableKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variableKey = variableKey;
      this.variableKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep scopeKey(
        final Object scopeKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.scopeKey = scopeKey;
      this.scopeKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedVariableResultBaseStrictContract build() {
      return new GeneratedVariableResultBaseStrictContract(
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceVariableKey(
              applyRequiredPolicy(this.variableKey, this.variableKeyPolicy, Fields.VARIABLE_KEY)),
          coerceScopeKey(applyRequiredPolicy(this.scopeKey, this.scopeKeyPolicy, Fields.SCOPE_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey));
    }
  }

  public interface NameStep {
    TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    VariableKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VariableKeyStep {
    ScopeKeyStep variableKey(
        final Object variableKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ScopeKeyStep {
    ProcessInstanceKeyStep scopeKey(
        final Object scopeKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

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
