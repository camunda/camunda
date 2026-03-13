/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedVariableFilterStrictContract(
    @Nullable Object name,
    @Nullable Object value,
    @Nullable String tenantId,
    @Nullable Boolean isTruncated,
    @Nullable Object variableKey,
    @Nullable Object scopeKey,
    @Nullable Object processInstanceKey) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object name;
    private Object value;
    private String tenantId;
    private Boolean isTruncated;
    private Object variableKey;
    private Object scopeKey;
    private Object processInstanceKey;

    private Builder() {}

    @Override
    public OptionalStep name(final @Nullable Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep value(final @Nullable Object value) {
      this.value = value;
      return this;
    }

    @Override
    public OptionalStep value(
        final @Nullable Object value, final ContractPolicy.FieldPolicy<Object> policy) {
      this.value = policy.apply(value, Fields.VALUE, null);
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
    public OptionalStep isTruncated(final @Nullable Boolean isTruncated) {
      this.isTruncated = isTruncated;
      return this;
    }

    @Override
    public OptionalStep isTruncated(
        final @Nullable Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isTruncated = policy.apply(isTruncated, Fields.IS_TRUNCATED, null);
      return this;
    }

    @Override
    public OptionalStep variableKey(final @Nullable Object variableKey) {
      this.variableKey = variableKey;
      return this;
    }

    @Override
    public OptionalStep variableKey(
        final @Nullable Object variableKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.variableKey = policy.apply(variableKey, Fields.VARIABLE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep scopeKey(final @Nullable Object scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    @Override
    public OptionalStep scopeKey(
        final @Nullable Object scopeKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.scopeKey = policy.apply(scopeKey, Fields.SCOPE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedVariableFilterStrictContract build() {
      return new GeneratedVariableFilterStrictContract(
          this.name,
          this.value,
          this.tenantId,
          this.isTruncated,
          this.variableKey,
          this.scopeKey,
          this.processInstanceKey);
    }
  }

  public interface OptionalStep {
    OptionalStep name(final @Nullable Object name);

    OptionalStep name(final @Nullable Object name, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep value(final @Nullable Object value);

    OptionalStep value(
        final @Nullable Object value, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep isTruncated(final @Nullable Boolean isTruncated);

    OptionalStep isTruncated(
        final @Nullable Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep variableKey(final @Nullable Object variableKey);

    OptionalStep variableKey(
        final @Nullable Object variableKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep scopeKey(final @Nullable Object scopeKey);

    OptionalStep scopeKey(
        final @Nullable Object scopeKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedVariableFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("VariableFilter", "name");
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("VariableFilter", "value");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("VariableFilter", "tenantId");
    public static final ContractPolicy.FieldRef IS_TRUNCATED =
        ContractPolicy.field("VariableFilter", "isTruncated");
    public static final ContractPolicy.FieldRef VARIABLE_KEY =
        ContractPolicy.field("VariableFilter", "variableKey");
    public static final ContractPolicy.FieldRef SCOPE_KEY =
        ContractPolicy.field("VariableFilter", "scopeKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("VariableFilter", "processInstanceKey");

    private Fields() {}
  }
}
