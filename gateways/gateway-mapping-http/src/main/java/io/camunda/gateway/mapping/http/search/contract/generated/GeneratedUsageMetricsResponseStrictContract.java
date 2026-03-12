/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUsageMetricsResponseStrictContract(
    Long activeTenants,
    java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants) {

  public GeneratedUsageMetricsResponseStrictContract {
    Objects.requireNonNull(activeTenants, "activeTenants is required and must not be null");
    Objects.requireNonNull(tenants, "tenants is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ActiveTenantsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ActiveTenantsStep, TenantsStep, OptionalStep {
    private Long activeTenants;
    private ContractPolicy.FieldPolicy<Long> activeTenantsPolicy;
    private java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants;
    private ContractPolicy.FieldPolicy<
            java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract>>
        tenantsPolicy;

    private Builder() {}

    @Override
    public TenantsStep activeTenants(
        final Long activeTenants, final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeTenants = activeTenants;
      this.activeTenantsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants,
        final ContractPolicy.FieldPolicy<
                java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract>>
            policy) {
      this.tenants = tenants;
      this.tenantsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedUsageMetricsResponseStrictContract build() {
      return new GeneratedUsageMetricsResponseStrictContract(
          applyRequiredPolicy(this.activeTenants, this.activeTenantsPolicy, Fields.ACTIVE_TENANTS),
          applyRequiredPolicy(this.tenants, this.tenantsPolicy, Fields.TENANTS));
    }
  }

  public interface ActiveTenantsStep {
    TenantsStep activeTenants(
        final Long activeTenants, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface TenantsStep {
    OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants,
        final ContractPolicy.FieldPolicy<
                java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract>>
            policy);
  }

  public interface OptionalStep {
    GeneratedUsageMetricsResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ACTIVE_TENANTS =
        ContractPolicy.field("UsageMetricsResponse", "activeTenants");
    public static final ContractPolicy.FieldRef TENANTS =
        ContractPolicy.field("UsageMetricsResponse", "tenants");

    private Fields() {}
  }
}
