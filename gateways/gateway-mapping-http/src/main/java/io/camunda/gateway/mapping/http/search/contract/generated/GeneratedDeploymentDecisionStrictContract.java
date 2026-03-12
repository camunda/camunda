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
public record GeneratedDeploymentDecisionStrictContract(
    String decisionDefinitionId,
    Integer version,
    String name,
    String tenantId,
    String decisionRequirementsId,
    String decisionDefinitionKey,
    String decisionRequirementsKey) {

  public GeneratedDeploymentDecisionStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsId, "decisionRequirementsId is required and must not be null");
    Objects.requireNonNull(
        decisionDefinitionKey, "decisionDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsKey, "decisionRequirementsKey is required and must not be null");
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
          VersionStep,
          NameStep,
          TenantIdStep,
          DecisionRequirementsIdStep,
          DecisionDefinitionKeyStep,
          DecisionRequirementsKeyStep,
          OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String decisionRequirementsId;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsIdPolicy;
    private Object decisionDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> decisionDefinitionKeyPolicy;
    private Object decisionRequirementsKey;
    private ContractPolicy.FieldPolicy<Object> decisionRequirementsKeyPolicy;

    private Builder() {}

    @Override
    public VersionStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.decisionDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public NameStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public DecisionDefinitionKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsId = decisionRequirementsId;
      this.decisionRequirementsIdPolicy = policy;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      this.decisionDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      this.decisionRequirementsKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDeploymentDecisionStrictContract build() {
      return new GeneratedDeploymentDecisionStrictContract(
          applyRequiredPolicy(
              this.decisionDefinitionId,
              this.decisionDefinitionIdPolicy,
              Fields.DECISION_DEFINITION_ID),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(
              this.decisionRequirementsId,
              this.decisionRequirementsIdPolicy,
              Fields.DECISION_REQUIREMENTS_ID),
          coerceDecisionDefinitionKey(
              applyRequiredPolicy(
                  this.decisionDefinitionKey,
                  this.decisionDefinitionKeyPolicy,
                  Fields.DECISION_DEFINITION_KEY)),
          coerceDecisionRequirementsKey(
              applyRequiredPolicy(
                  this.decisionRequirementsKey,
                  this.decisionRequirementsKeyPolicy,
                  Fields.DECISION_REQUIREMENTS_KEY)));
    }
  }

  public interface DecisionDefinitionIdStep {
    VersionStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    NameStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface NameStep {
    TenantIdStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    DecisionRequirementsIdStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionRequirementsIdStep {
    DecisionDefinitionKeyStep decisionRequirementsId(
        final String decisionRequirementsId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionRequirementsKeyStep decisionDefinitionKey(
        final Object decisionDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface DecisionRequirementsKeyStep {
    OptionalStep decisionRequirementsKey(
        final Object decisionRequirementsKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedDeploymentDecisionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DeploymentDecisionResult", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DeploymentDecisionResult", "version");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("DeploymentDecisionResult", "name");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DeploymentDecisionResult", "tenantId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DeploymentDecisionResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DeploymentDecisionResult", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DeploymentDecisionResult", "decisionRequirementsKey");

    private Fields() {}
  }
}
