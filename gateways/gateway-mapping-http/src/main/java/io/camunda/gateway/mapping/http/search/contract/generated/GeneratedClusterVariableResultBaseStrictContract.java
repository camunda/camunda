/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml#/components/schemas/ClusterVariableResultBase
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedClusterVariableResultBaseStrictContract(
    @JsonProperty("name") String name,
    @JsonProperty("scope")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableScopeEnum
            scope,
    @JsonProperty("tenantId") @Nullable String tenantId) {

  public GeneratedClusterVariableResultBaseStrictContract {
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(scope, "No scope provided.");
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, ScopeStep, OptionalStep {
    private String name;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedClusterVariableScopeEnum
        scope;
    private String tenantId;

    private Builder() {}

    @Override
    public ScopeStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep scope(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedClusterVariableScopeEnum
            scope) {
      this.scope = scope;
      return this;
    }

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
    public GeneratedClusterVariableResultBaseStrictContract build() {
      return new GeneratedClusterVariableResultBaseStrictContract(
          this.name, this.scope, this.tenantId);
    }
  }

  public interface NameStep {
    ScopeStep name(final String name);
  }

  public interface ScopeStep {
    OptionalStep scope(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedClusterVariableScopeEnum
            scope);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

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
