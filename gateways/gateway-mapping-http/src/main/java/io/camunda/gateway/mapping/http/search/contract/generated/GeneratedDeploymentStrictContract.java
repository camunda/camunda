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
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentStrictContract(
    @JsonProperty("deploymentKey") String deploymentKey,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("deployments")
        java.util.List<GeneratedDeploymentMetadataStrictContract> deployments) {

  public GeneratedDeploymentStrictContract {
    Objects.requireNonNull(deploymentKey, "No deploymentKey provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(deployments, "No deployments provided.");
  }

  public static String coerceDeploymentKey(final Object value) {
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
        "deploymentKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static java.util.List<GeneratedDeploymentMetadataStrictContract> coerceDeployments(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "deployments must be a List of GeneratedDeploymentMetadataStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedDeploymentMetadataStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedDeploymentMetadataStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "deployments must contain only GeneratedDeploymentMetadataStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static DeploymentKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DeploymentKeyStep, TenantIdStep, DeploymentsStep, OptionalStep {
    private Object deploymentKey;
    private String tenantId;
    private Object deployments;

    private Builder() {}

    @Override
    public TenantIdStep deploymentKey(final Object deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    @Override
    public DeploymentsStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep deployments(final Object deployments) {
      this.deployments = deployments;
      return this;
    }

    @Override
    public GeneratedDeploymentStrictContract build() {
      return new GeneratedDeploymentStrictContract(
          coerceDeploymentKey(this.deploymentKey),
          this.tenantId,
          coerceDeployments(this.deployments));
    }
  }

  public interface DeploymentKeyStep {
    TenantIdStep deploymentKey(final Object deploymentKey);
  }

  public interface TenantIdStep {
    DeploymentsStep tenantId(final String tenantId);
  }

  public interface DeploymentsStep {
    OptionalStep deployments(final Object deployments);
  }

  public interface OptionalStep {
    GeneratedDeploymentStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DEPLOYMENT_KEY =
        ContractPolicy.field("DeploymentResult", "deploymentKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DeploymentResult", "tenantId");
    public static final ContractPolicy.FieldRef DEPLOYMENTS =
        ContractPolicy.field("DeploymentResult", "deployments");

    private Fields() {}
  }
}
