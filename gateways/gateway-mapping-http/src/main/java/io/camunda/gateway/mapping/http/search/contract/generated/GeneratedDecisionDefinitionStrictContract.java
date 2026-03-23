/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-definitions.yaml#/components/schemas/DecisionDefinitionResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionDefinitionStrictContract(
    @JsonProperty("decisionDefinitionId") String decisionDefinitionId,
    @JsonProperty("decisionDefinitionKey") String decisionDefinitionKey,
    @JsonProperty("decisionRequirementsId") String decisionRequirementsId,
    @JsonProperty("decisionRequirementsKey") String decisionRequirementsKey,
    @JsonProperty("decisionRequirementsName") String decisionRequirementsName,
    @JsonProperty("decisionRequirementsVersion") Integer decisionRequirementsVersion,
    @JsonProperty("name") String name,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("version") Integer version) {

  public GeneratedDecisionDefinitionStrictContract {
    Objects.requireNonNull(decisionDefinitionId, "No decisionDefinitionId provided.");
    Objects.requireNonNull(decisionDefinitionKey, "No decisionDefinitionKey provided.");
    Objects.requireNonNull(decisionRequirementsId, "No decisionRequirementsId provided.");
    Objects.requireNonNull(decisionRequirementsKey, "No decisionRequirementsKey provided.");
    Objects.requireNonNull(decisionRequirementsName, "No decisionRequirementsName provided.");
    Objects.requireNonNull(decisionRequirementsVersion, "No decisionRequirementsVersion provided.");
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(version, "No version provided.");
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
