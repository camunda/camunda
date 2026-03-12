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
import java.util.ArrayList;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentStrictContract(
    String deploymentKey,
    String tenantId,
    java.util.List<GeneratedDeploymentMetadataStrictContract> deployments) {

  public GeneratedDeploymentStrictContract {
    Objects.requireNonNull(deploymentKey, "deploymentKey is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(deployments, "deployments is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static DeploymentKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements DeploymentKeyStep, TenantIdStep, DeploymentsStep, OptionalStep {
    private Object deploymentKey;
    private ContractPolicy.FieldPolicy<Object> deploymentKeyPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object deployments;
    private ContractPolicy.FieldPolicy<Object> deploymentsPolicy;

    private Builder() {}

    @Override
    public TenantIdStep deploymentKey(
        final Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deploymentKey = deploymentKey;
      this.deploymentKeyPolicy = policy;
      return this;
    }

    @Override
    public DeploymentsStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep deployments(
        final Object deployments, final ContractPolicy.FieldPolicy<Object> policy) {
      this.deployments = deployments;
      this.deploymentsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDeploymentStrictContract build() {
      return new GeneratedDeploymentStrictContract(
          coerceDeploymentKey(
              applyRequiredPolicy(
                  this.deploymentKey, this.deploymentKeyPolicy, Fields.DEPLOYMENT_KEY)),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceDeployments(
              applyRequiredPolicy(this.deployments, this.deploymentsPolicy, Fields.DEPLOYMENTS)));
    }
  }

  public interface DeploymentKeyStep {
    TenantIdStep deploymentKey(
        final Object deploymentKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TenantIdStep {
    DeploymentsStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DeploymentsStep {
    OptionalStep deployments(
        final Object deployments, final ContractPolicy.FieldPolicy<Object> policy);
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
