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
public record GeneratedClusterVariableResultBaseStrictContract(
    String name,
    io.camunda.gateway.protocol.model.ClusterVariableScopeEnum scope,
    @Nullable String tenantId) {

  public GeneratedClusterVariableResultBaseStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(scope, "scope is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, ScopeStep, OptionalStep {
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private io.camunda.gateway.protocol.model.ClusterVariableScopeEnum scope;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ClusterVariableScopeEnum>
        scopePolicy;
    private String tenantId;

    private Builder() {}

    @Override
    public ScopeStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep scope(
        final io.camunda.gateway.protocol.model.ClusterVariableScopeEnum scope,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ClusterVariableScopeEnum>
            policy) {
      this.scope = scope;
      this.scopePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedClusterVariableResultBaseStrictContract build() {
      return new GeneratedClusterVariableResultBaseStrictContract(
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(this.scope, this.scopePolicy, Fields.SCOPE),
          this.tenantId);
    }
  }

  public interface NameStep {
    ScopeStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ScopeStep {
    OptionalStep scope(
        final io.camunda.gateway.protocol.model.ClusterVariableScopeEnum scope,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ClusterVariableScopeEnum>
            policy);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedClusterVariableResultBaseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("ClusterVariableResultBase", "name");
    public static final ContractPolicy.FieldRef SCOPE =
        ContractPolicy.field("ClusterVariableResultBase", "scope");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ClusterVariableResultBase", "tenantId");

    private Fields() {}
  }
}
