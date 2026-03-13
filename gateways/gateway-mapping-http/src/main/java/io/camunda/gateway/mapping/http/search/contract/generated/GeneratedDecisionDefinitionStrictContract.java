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
import org.jspecify.annotations.NullMarked;

@NullMarked
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
    private Object decisionDefinitionKey;
    private String decisionRequirementsId;
    private Object decisionRequirementsKey;
    private String decisionRequirementsName;
    private Integer decisionRequirementsVersion;
    private String name;
    private String tenantId;
    private Integer version;

    private Builder() {}

    @Override
    public DecisionDefinitionKeyStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public DecisionRequirementsNameStep decisionRequirementsKey(
        final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public DecisionRequirementsVersionStep decisionRequirementsName(
        final String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    @Override
    public NameStep decisionRequirementsVersion(final Integer decisionRequirementsVersion) {
      this.decisionRequirementsVersion = decisionRequirementsVersion;
      return this;
    }

    @Override
    public TenantIdStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public VersionStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public GeneratedDecisionDefinitionStrictContract build() {
      return new GeneratedDecisionDefinitionStrictContract(
          this.decisionDefinitionId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          this.decisionRequirementsId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey),
          this.decisionRequirementsName,
          this.decisionRequirementsVersion,
          this.name,
          this.tenantId,
          this.version);
    }
  }

  public interface DecisionDefinitionIdStep {
    DecisionDefinitionKeyStep decisionDefinitionId(final String decisionDefinitionId);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionRequirementsIdStep decisionDefinitionKey(final Object decisionDefinitionKey);
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsKeyStep decisionRequirementsId(final String decisionRequirementsId);
  }

  public interface DecisionRequirementsKeyStep {
    DecisionRequirementsNameStep decisionRequirementsKey(final Object decisionRequirementsKey);
  }

  public interface DecisionRequirementsNameStep {
    DecisionRequirementsVersionStep decisionRequirementsName(final String decisionRequirementsName);
  }

  public interface DecisionRequirementsVersionStep {
    NameStep decisionRequirementsVersion(final Integer decisionRequirementsVersion);
  }

  public interface NameStep {
    TenantIdStep name(final String name);
  }

  public interface TenantIdStep {
    VersionStep tenantId(final String tenantId);
  }

  public interface VersionStep {
    OptionalStep version(final Integer version);
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
