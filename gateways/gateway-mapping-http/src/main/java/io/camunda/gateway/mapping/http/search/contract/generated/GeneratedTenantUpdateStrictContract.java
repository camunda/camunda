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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTenantUpdateStrictContract(
    String tenantId, String name, @Nullable String description) {

  public GeneratedTenantUpdateStrictContract {
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements TenantIdStep, NameStep, OptionalStep {
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String description;

    private Builder() {}

    @Override
    public NameStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep description(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public OptionalStep description(
        final String description, final ContractPolicy.FieldPolicy<String> policy) {
      this.description = policy.apply(description, Fields.DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedTenantUpdateStrictContract build() {
      return new GeneratedTenantUpdateStrictContract(
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          this.description);
    }
  }

  public interface TenantIdStep {
    NameStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface NameStep {
    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep description(final String description);

    OptionalStep description(
        final String description, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedTenantUpdateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("TenantUpdateResult", "tenantId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("TenantUpdateResult", "name");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("TenantUpdateResult", "description");

    private Fields() {}
  }
}
