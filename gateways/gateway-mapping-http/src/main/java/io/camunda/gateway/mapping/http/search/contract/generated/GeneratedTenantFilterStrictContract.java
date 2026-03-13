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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTenantFilterStrictContract(
    @Nullable String tenantId, @Nullable String name) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String tenantId;
    private String name;

    private Builder() {}

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public GeneratedTenantFilterStrictContract build() {
      return new GeneratedTenantFilterStrictContract(this.tenantId, this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedTenantFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("TenantFilter", "tenantId");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("TenantFilter", "name");

    private Fields() {}
  }
}
