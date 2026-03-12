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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionRequirementsFilterStrictContract(
    @Nullable String decisionRequirementsName,
    @Nullable String decisionRequirementsId,
    @Nullable String decisionRequirementsKey,
    @Nullable Integer version,
    @Nullable String tenantId,
    @Nullable String resourceName) {

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    public OptionalStep decisionRequirementsName(final String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsName =
          policy.apply(decisionRequirementsName, Fields.DECISION_REQUIREMENTS_NAME, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId =
          policy.apply(decisionRequirementsId, Fields.DECISION_REQUIREMENTS_ID, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final String decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public Builder decisionRequirementsKey(
        final String decisionRequirementsKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey =
          policy.apply(decisionRequirementsKey, Fields.DECISION_REQUIREMENTS_KEY, null);
      return this;
    }

    @Override
    public OptionalStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public OptionalStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = policy.apply(version, Fields.VERSION, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public OptionalStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
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
    OptionalStep decisionRequirementsName(final String decisionRequirementsName);

    OptionalStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsId(final String decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(final String decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final String decisionRequirementsKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep version(final Integer version);

    OptionalStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep resourceName(final String resourceName);

    OptionalStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);

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
