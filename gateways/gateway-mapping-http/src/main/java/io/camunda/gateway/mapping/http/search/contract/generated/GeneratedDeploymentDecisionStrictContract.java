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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentDecisionStrictContract(
    @JsonProperty("decisionDefinitionId") String decisionDefinitionId,
    @JsonProperty("version") Integer version,
    @JsonProperty("name") String name,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("decisionRequirementsId") String decisionRequirementsId,
    @JsonProperty("decisionDefinitionKey") String decisionDefinitionKey,
    @JsonProperty("decisionRequirementsKey") String decisionRequirementsKey) {

  public GeneratedDeploymentDecisionStrictContract {
    Objects.requireNonNull(decisionDefinitionId, "No decisionDefinitionId provided.");
    Objects.requireNonNull(version, "No version provided.");
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(decisionRequirementsId, "No decisionRequirementsId provided.");
    Objects.requireNonNull(decisionDefinitionKey, "No decisionDefinitionKey provided.");
    Objects.requireNonNull(decisionRequirementsKey, "No decisionRequirementsKey provided.");
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
          VersionStep,
          NameStep,
          TenantIdStep,
          DecisionRequirementsIdStep,
          DecisionDefinitionKeyStep,
          DecisionRequirementsKeyStep,
          OptionalStep {
    private String decisionDefinitionId;
    private Integer version;
    private String name;
    private String tenantId;
    private String decisionRequirementsId;
    private Object decisionDefinitionKey;
    private Object decisionRequirementsKey;

    private Builder() {}

    @Override
    public VersionStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    @Override
    public NameStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public TenantIdStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public DecisionRequirementsIdStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public DecisionDefinitionKeyStep decisionRequirementsId(final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public GeneratedDeploymentDecisionStrictContract build() {
      return new GeneratedDeploymentDecisionStrictContract(
          this.decisionDefinitionId,
          this.version,
          this.name,
          this.tenantId,
          this.decisionRequirementsId,
          coerceDecisionDefinitionKey(this.decisionDefinitionKey),
          coerceDecisionRequirementsKey(this.decisionRequirementsKey));
    }
  }

  public interface DecisionDefinitionIdStep {
    VersionStep decisionDefinitionId(final String decisionDefinitionId);
  }

  public interface VersionStep {
    NameStep version(final Integer version);
  }

  public interface NameStep {
    TenantIdStep name(final String name);
  }

  public interface TenantIdStep {
    DecisionRequirementsIdStep tenantId(final String tenantId);
  }

  public interface DecisionRequirementsIdStep {
    DecisionDefinitionKeyStep decisionRequirementsId(final String decisionRequirementsId);
  }

  public interface DecisionDefinitionKeyStep {
    DecisionRequirementsKeyStep decisionDefinitionKey(final Object decisionDefinitionKey);
  }

  public interface DecisionRequirementsKeyStep {
    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);
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
