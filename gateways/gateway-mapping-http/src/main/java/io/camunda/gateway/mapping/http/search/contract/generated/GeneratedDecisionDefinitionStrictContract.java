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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionDefinitionStrictContract(
    String decisionDefinitionId,
    String decisionDefinitionKey,
    String decisionRequirementsId,
    String decisionRequirementsKey,
    String decisionRequirementsName,
    Integer decisionRequirementsVersion,
    String name,
    String tenantId,
    Integer version) {

  public GeneratedDecisionDefinitionStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsId, "decisionRequirementsId is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsKey, "decisionRequirementsKey is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsName, "decisionRequirementsName is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsVersion,
        "decisionRequirementsVersion is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
  }

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

  public static DecisionDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DecisionDefinitionIdStep,
          DecisionDefinitionKeyStep,
          DecisionRequirementsIdStep,
          DecisionRequirementsKeyStep,
          DecisionRequirementsNameStep,
          DecisionRequirementsVersionStep,
          NameStep,
          TenantIdStep,
          VersionStep,
          OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private Object decisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> decisionDefinitionKeyPolicy;
    private String decisionRequirementsId;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsIdPolicy;
    private Object decisionRequirementsKey;
    private ContractPolicy.FieldPolicy<Object> decisionRequirementsKeyPolicy;
    private String decisionRequirementsName;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsNamePolicy;
    private Integer decisionRequirementsVersion;
    private ContractPolicy.FieldPolicy<Integer> decisionRequirementsVersionPolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;

    private Builder() {}

    @Override
    public DecisionDefinitionKeyStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.decisionDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      this.decisionDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId = decisionRequirementsId;
      this.decisionRequirementsIdPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsNameStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      this.decisionRequirementsKeyPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsVersionStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsName = decisionRequirementsName;
      this.decisionRequirementsNamePolicy = policy;
      return this;
    }

    @Override
    public NameStep decisionRequirementsVersion(
        final Integer decisionRequirementsVersion,
        final ContractPolicy.FieldPolicy<Integer> policy) {
      this.decisionRequirementsVersion = decisionRequirementsVersion;
      this.decisionRequirementsVersionPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public VersionStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDecisionDefinitionStrictContract build() {
      return new GeneratedDecisionDefinitionStrictContract(
          applyRequiredPolicy(
              this.decisionDefinitionId,
              this.decisionDefinitionIdPolicy,
              Fields.DECISION_DEFINITION_ID),
          coerceDecisionDefinitionKey(
              applyRequiredPolicy(
                  this.decisionDefinitionKey,
                  this.decisionDefinitionKeyPolicy,
                  Fields.DECISION_DEFINITION_KEY)),
          applyRequiredPolicy(
              this.decisionRequirementsId,
              this.decisionRequirementsIdPolicy,
              Fields.DECISION_REQUIREMENTS_ID),
          coerceDecisionRequirementsKey(
              applyRequiredPolicy(
                  this.decisionRequirementsKey,
                  this.decisionRequirementsKeyPolicy,
                  Fields.DECISION_REQUIREMENTS_KEY)),
          applyRequiredPolicy(
              this.decisionRequirementsName,
              this.decisionRequirementsNamePolicy,
              Fields.DECISION_REQUIREMENTS_NAME),
          applyRequiredPolicy(
              this.decisionRequirementsVersion,
              this.decisionRequirementsVersionPolicy,
              Fields.DECISION_REQUIREMENTS_VERSION),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION));
    }
  }

  public interface DecisionDefinitionIdStep {
    DecisionDefinitionKeyStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionRequirementsIdStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionRequirementsKeyStep {
    DecisionRequirementsNameStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionRequirementsNameStep {
    DecisionRequirementsVersionStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionRequirementsVersionStep {
    NameStep decisionRequirementsVersion(
        final Integer decisionRequirementsVersion,
        final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface NameStep {
    TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    VersionStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    OptionalStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedDecisionDefinitionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DecisionDefinitionResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionDefinitionResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DecisionDefinitionResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DecisionDefinitionResult", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME =
        ContractPolicy.field("DecisionDefinitionResult", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_VERSION =
        ContractPolicy.field("DecisionDefinitionResult", "decisionRequirementsVersion");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("DecisionDefinitionResult", "name");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionDefinitionResult", "tenantId");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DecisionDefinitionResult", "version");

    private Fields() {}
  }
}
