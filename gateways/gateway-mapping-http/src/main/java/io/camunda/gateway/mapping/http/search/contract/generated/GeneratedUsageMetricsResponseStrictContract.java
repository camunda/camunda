/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUsageMetricsResponseStrictContract(
    Long activeTenants,
    java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants) {

  public GeneratedUsageMetricsResponseStrictContract {
    Objects.requireNonNull(activeTenants, "activeTenants is required and must not be null");
    Objects.requireNonNull(tenants, "tenants is required and must not be null");
  }

  public static ActiveTenantsStep builder() {
    return new Builder();
  }

  public static final class Builder implements ActiveTenantsStep, TenantsStep, OptionalStep {
    private Long activeTenants;
    private java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants;

    private Builder() {}

    @Override
    public TenantsStep activeTenants(final Long activeTenants) {
      this.activeTenants = activeTenants;
      return this;
    }

    @Override
    public OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants) {
      this.tenants = tenants;
      return this;
    }

    @Override
    public GeneratedUsageMetricsResponseStrictContract build() {
      return new GeneratedUsageMetricsResponseStrictContract(this.activeTenants, this.tenants);
    }
  }

  public interface ActiveTenantsStep {
    TenantsStep activeTenants(final Long activeTenants);
  }

  public interface TenantsStep {
    OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants);
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
