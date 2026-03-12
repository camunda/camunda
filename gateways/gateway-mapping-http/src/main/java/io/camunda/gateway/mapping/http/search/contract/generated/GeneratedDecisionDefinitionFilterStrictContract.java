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
public record GeneratedDecisionDefinitionFilterStrictContract(
    @Nullable String decisionDefinitionId,
    @Nullable String name,
    @Nullable Boolean isLatestVersion,
    @Nullable Integer version,
    @Nullable String decisionRequirementsId,
    @Nullable String tenantId,
    @Nullable String decisionDefinitionKey,
    @Nullable String decisionRequirementsKey,
    @Nullable String decisionRequirementsName,
    @Nullable Integer decisionRequirementsVersion) {

  public static String coerceDecisionDefinitionKey(final Object value) {
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
        "decisionDefinitionKey must be a String or Number, but was " + value.getClass().getName());
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
    private String decisionDefinitionId;
    private String name;
    private Boolean isLatestVersion;
    private Integer version;
    private String decisionRequirementsId;
    private String tenantId;
    private Object decisionDefinitionKey;
    private Object decisionRequirementsKey;
    private String decisionRequirementsName;
    private Integer decisionRequirementsVersion;

    private Builder() {}

    @Override
    public OptionalStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId =
          policy.apply(decisionDefinitionId, Fields.DECISION_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep isLatestVersion(final Boolean isLatestVersion) {
      this.isLatestVersion = isLatestVersion;
      return this;
    }

    @Override
    public OptionalStep isLatestVersion(
        final Boolean isLatestVersion, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isLatestVersion = policy.apply(isLatestVersion, Fields.IS_LATEST_VERSION, null);
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
    public OptionalStep decisionDefinitionKey(final String decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public Builder decisionDefinitionKey(
        final String decisionDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey =
          policy.apply(decisionDefinitionKey, Fields.DECISION_DEFINITION_KEY, null);
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
    public OptionalStep decisionRequirementsVersion(final Integer decisionRequirementsVersion) {
      this.decisionRequirementsVersion = decisionRequirementsVersion;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsVersion(
        final Integer decisionRequirementsVersion,
        final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionRequirementsVersion =
          policy.apply(decisionRequirementsVersion, Fields.DECISION_REQUIREMENTS_VERSION, null);
      return this;
    }

    @Override
    public GeneratedDecisionDefinitionFilterStrictContract build() {
      return new GeneratedDecisionDefinitionFilterStrictContract(
          this.decisionDefinitionId,
          this.name,
          this.isLatestVersion,
          this.version,
          this.decisionRequirementsId,
          this.tenantId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          this.decisionRequirementsName,
          this.decisionRequirementsVersion);
    }
  }

  public interface OptionalStep {
    OptionalStep decisionDefinitionId(final String decisionDefinitionId);

    OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep isLatestVersion(final Boolean isLatestVersion);

    OptionalStep isLatestVersion(
        final Boolean isLatestVersion, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep version(final Integer version);

    OptionalStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep decisionRequirementsId(final String decisionRequirementsId);

    OptionalStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(final String decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey);

    OptionalStep decisionDefinitionKey(
        final String decisionDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsKey(final String decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);

    OptionalStep decisionRequirementsKey(
        final String decisionRequirementsKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirementsName(final String decisionRequirementsName);

    OptionalStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep decisionRequirementsVersion(final Integer decisionRequirementsVersion);

    OptionalStep decisionRequirementsVersion(
        final Integer decisionRequirementsVersion,
        final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedDecisionDefinitionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("DecisionDefinitionFilter", "name");
    public static final ContractPolicy.FieldRef IS_LATEST_VERSION =
        ContractPolicy.field("DecisionDefinitionFilter", "isLatestVersion");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DecisionDefinitionFilter", "version");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionDefinitionFilter", "tenantId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_VERSION =
        ContractPolicy.field("DecisionDefinitionFilter", "decisionRequirementsVersion");

    private Fields() {}
  }
}
