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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedVariableFilterStrictContract(
    @JsonProperty("name") @Nullable GeneratedStringFilterPropertyStrictContract name,
    @JsonProperty("value") @Nullable GeneratedStringFilterPropertyStrictContract value,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("isTruncated") @Nullable Boolean isTruncated,
    @JsonProperty("variableKey")
        @Nullable GeneratedVariableKeyFilterPropertyStrictContract variableKey,
    @JsonProperty("scopeKey") @Nullable GeneratedScopeKeyFilterPropertyStrictContract scopeKey,
    @JsonProperty("processInstanceKey") @Nullable Object processInstanceKey) {

  public GeneratedVariableFilterStrictContract {
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

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract name;
    private GeneratedStringFilterPropertyStrictContract value;
    private String tenantId;
    private Boolean isTruncated;
    private GeneratedVariableKeyFilterPropertyStrictContract variableKey;
    private GeneratedScopeKeyFilterPropertyStrictContract scopeKey;
    private Object processInstanceKey;

    private Builder() {}

    @Override
    public OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep value(final @Nullable GeneratedStringFilterPropertyStrictContract value) {
      this.value = value;
      return this;
    }

    @Override
    public OptionalStep value(
        final @Nullable GeneratedStringFilterPropertyStrictContract value,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
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
    public OptionalStep variableKey(
        final @Nullable GeneratedVariableKeyFilterPropertyStrictContract variableKey) {
      this.variableKey = variableKey;
      return this;
    }

    @Override
    public OptionalStep variableKey(
        final @Nullable GeneratedVariableKeyFilterPropertyStrictContract variableKey,
        final ContractPolicy.FieldPolicy<GeneratedVariableKeyFilterPropertyStrictContract> policy) {
      this.variableKey = policy.apply(variableKey, Fields.VARIABLE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep scopeKey(
        final @Nullable GeneratedScopeKeyFilterPropertyStrictContract scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    @Override
    public OptionalStep scopeKey(
        final @Nullable GeneratedScopeKeyFilterPropertyStrictContract scopeKey,
        final ContractPolicy.FieldPolicy<GeneratedScopeKeyFilterPropertyStrictContract> policy) {
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
    OptionalStep name(final @Nullable GeneratedStringFilterPropertyStrictContract name);

    OptionalStep name(
        final @Nullable GeneratedStringFilterPropertyStrictContract name,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep value(final @Nullable GeneratedStringFilterPropertyStrictContract value);

    OptionalStep value(
        final @Nullable GeneratedStringFilterPropertyStrictContract value,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep isTruncated(final @Nullable Boolean isTruncated);

    OptionalStep isTruncated(
        final @Nullable Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep variableKey(
        final @Nullable GeneratedVariableKeyFilterPropertyStrictContract variableKey);

    OptionalStep variableKey(
        final @Nullable GeneratedVariableKeyFilterPropertyStrictContract variableKey,
        final ContractPolicy.FieldPolicy<GeneratedVariableKeyFilterPropertyStrictContract> policy);

    OptionalStep scopeKey(final @Nullable GeneratedScopeKeyFilterPropertyStrictContract scopeKey);

    OptionalStep scopeKey(
        final @Nullable GeneratedScopeKeyFilterPropertyStrictContract scopeKey,
        final ContractPolicy.FieldPolicy<GeneratedScopeKeyFilterPropertyStrictContract> policy);

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
