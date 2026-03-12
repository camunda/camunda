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
public record GeneratedDecisionRequirementsStrictContract(
    String decisionRequirementsId,
    String decisionRequirementsKey,
    String decisionRequirementsName,
    String resourceName,
    String tenantId,
    Integer version) {

  public GeneratedDecisionRequirementsStrictContract {
    Objects.requireNonNull(
        decisionRequirementsId, "decisionRequirementsId is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsKey, "decisionRequirementsKey is required and must not be null");
    Objects.requireNonNull(
        decisionRequirementsName, "decisionRequirementsName is required and must not be null");
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
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

  public static DecisionRequirementsIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DecisionRequirementsIdStep,
          DecisionRequirementsKeyStep,
          DecisionRequirementsNameStep,
          ResourceNameStep,
          TenantIdStep,
          VersionStep,
          OptionalStep {
    private String decisionRequirementsId;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsIdPolicy;
    private Object decisionRequirementsKey;
    private ContractPolicy.FieldPolicy<Object> decisionRequirementsKeyPolicy;
    private String decisionRequirementsName;
    private ContractPolicy.FieldPolicy<String> decisionRequirementsNamePolicy;
    private String resourceName;
    private ContractPolicy.FieldPolicy<String> resourceNamePolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;

    private Builder() {}

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
    public ResourceNameStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionRequirementsName = decisionRequirementsName;
      this.decisionRequirementsNamePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = resourceName;
      this.resourceNamePolicy = policy;
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
    public GeneratedDecisionRequirementsStrictContract build() {
      return new GeneratedDecisionRequirementsStrictContract(
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
          applyRequiredPolicy(this.resourceName, this.resourceNamePolicy, Fields.RESOURCE_NAME),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION));
    }
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
    ResourceNameStep decisionRequirementsName(
        final String decisionRequirementsName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    VersionStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    OptionalStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OptionalStep {
    GeneratedDecisionRequirementsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsKey");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME =
        ContractPolicy.field("DecisionRequirementsResult", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("DecisionRequirementsResult", "resourceName");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionRequirementsResult", "tenantId");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DecisionRequirementsResult", "version");

    private Fields() {}
  }
}
