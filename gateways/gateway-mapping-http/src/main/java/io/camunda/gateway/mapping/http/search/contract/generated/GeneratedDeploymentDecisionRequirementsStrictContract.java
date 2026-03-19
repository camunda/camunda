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
public record GeneratedDeploymentDecisionRequirementsStrictContract(
    @JsonProperty("decisionRequirementsId") String decisionRequirementsId,
    @JsonProperty("decisionRequirementsName") String decisionRequirementsName,
    @JsonProperty("version") Integer version,
    @JsonProperty("resourceName") String resourceName,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("decisionRequirementsKey") String decisionRequirementsKey) {

  public GeneratedDeploymentDecisionRequirementsStrictContract {
    Objects.requireNonNull(decisionRequirementsId, "No decisionRequirementsId provided.");
    Objects.requireNonNull(decisionRequirementsName, "No decisionRequirementsName provided.");
    Objects.requireNonNull(version, "No version provided.");
    Objects.requireNonNull(resourceName, "No resourceName provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(decisionRequirementsKey, "No decisionRequirementsKey provided.");
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

  public static DecisionRequirementsIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DecisionRequirementsIdStep,
          DecisionRequirementsNameStep,
          VersionStep,
          ResourceNameStep,
          TenantIdStep,
          DecisionRequirementsKeyStep,
          OptionalStep {
    private String decisionRequirementsId;
    private String decisionRequirementsName;
    private Integer version;
    private String resourceName;
    private String tenantId;
    private Object decisionRequirementsKey;

    private Builder() {}

    @Override
    public DecisionRequirementsNameStep decisionRequirementsId(
        final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    @Override
    public VersionStep decisionRequirementsName(final String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    @Override
    public ResourceNameStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public TenantIdStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public DecisionRequirementsKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    @Override
    public GeneratedDeploymentDecisionRequirementsStrictContract build() {
      return new GeneratedDeploymentDecisionRequirementsStrictContract(
          this.decisionRequirementsId,
          this.decisionRequirementsName,
          this.version,
          this.resourceName,
          this.tenantId,
          coerceDecisionRequirementsKey(this.decisionRequirementsKey));
    }
  }

  public interface DecisionRequirementsIdStep {
    DecisionRequirementsNameStep decisionRequirementsId(final String decisionRequirementsId);
  }

  public interface DecisionRequirementsNameStep {
    VersionStep decisionRequirementsName(final String decisionRequirementsName);
  }

  public interface VersionStep {
    ResourceNameStep version(final Integer version);
  }

  public interface ResourceNameStep {
    TenantIdStep resourceName(final String resourceName);
  }

  public interface TenantIdStep {
    DecisionRequirementsKeyStep tenantId(final String tenantId);
  }

  public interface DecisionRequirementsKeyStep {
    OptionalStep decisionRequirementsKey(final Object decisionRequirementsKey);
  }

  public interface OptionalStep {
    GeneratedDeploymentDecisionRequirementsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_ID =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "decisionRequirementsId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_NAME =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "decisionRequirementsName");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "version");
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "resourceName");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "tenantId");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS_KEY =
        ContractPolicy.field("DeploymentDecisionRequirementsResult", "decisionRequirementsKey");

    private Fields() {}
  }
}
