/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-requirements.yaml#/components/schemas/DecisionRequirementsFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionRequirementsFilterStrictContract(
    @JsonProperty("decisionRequirementsName") @Nullable String decisionRequirementsName,
    @JsonProperty("decisionRequirementsId") @Nullable String decisionRequirementsId,
    @JsonProperty("decisionRequirementsKey") @Nullable String decisionRequirementsKey,
    @JsonProperty("version") @Nullable Integer version,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("resourceName") @Nullable String resourceName) {

  public GeneratedDecisionRequirementsFilterStrictContract {
    if (decisionRequirementsKey != null)
      if (decisionRequirementsKey.isBlank())
        throw new IllegalArgumentException("decisionRequirementsKey must not be blank");
    if (decisionRequirementsKey != null)
      if (decisionRequirementsKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided decisionRequirementsKey exceeds the limit of 25 characters.");
    if (decisionRequirementsKey != null)
      if (!decisionRequirementsKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided decisionRequirementsKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
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

  public static String coerceDecisionRequirementsKey(final Object value) {
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
        "decisionRequirementsKey must be a String or Number, but was "
            + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String decisionRequirementsName;
    private String decisionRequirementsId;
    private Object decisionRequirementsKey;
    private Integer version;
    private String tenantId;
    private String resourceName;

    private Builder() {}

    @Override
    public OptionalStep decisionRequirementsName(final @Nullable String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsName(
        final @Nullable String decisionRequirementsName,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsName =
          policy.apply(decisionRequirementsName, Fields.DECISION_REQUIREMENTS_NAME, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(final @Nullable String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final @Nullable String decisionRequirementsId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final @Nullable String decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionRequirementsKey(
        final @Nullable String decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final @Nullable Object decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep version(final @Nullable Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public OptionalStep version(
        final @Nullable Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = policy.apply(version, Fields.VERSION, null);
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
    public OptionalStep resourceName(final @Nullable String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public OptionalStep resourceName(
        final @Nullable String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = policy.apply(resourceName, Fields.RESOURCE_NAME, null);
      return this;
    }

    @Override
    public GeneratedDecisionRequirementsFilterStrictContract build() {
      return new GeneratedDecisionRequirementsFilterStrictContract(
          this.decisionRequirementsName,
          this.decisionRequirementsId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          this.version,
          this.tenantId,
          this.resourceName);
    }
  }

  public interface OptionalStep {
    OptionalStep decisionRequirementsName(final @Nullable String decisionRequirementsName);

    OptionalStep decisionRequirementsName(
        final @Nullable String decisionRequirementsName,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsId(final @Nullable String decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final @Nullable String decisionRequirementsId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(final @Nullable String decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(final @Nullable Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final @Nullable String decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(
        final @Nullable Object decisionRequirementsKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep version(final @Nullable Integer version);

    OptionalStep version(
        final @Nullable Integer version, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep resourceName(final @Nullable String resourceName);

    OptionalStep resourceName(
        final @Nullable String resourceName, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedDecisionRequirementsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME =
        ContractPolicy.field("DecisionRequirementsFilter", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DecisionRequirementsFilter", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DecisionRequirementsFilter", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DecisionRequirementsFilter", "version");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionRequirementsFilter", "tenantId");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("DecisionRequirementsFilter", "resourceName");

    private Fields() {}
  }
}
