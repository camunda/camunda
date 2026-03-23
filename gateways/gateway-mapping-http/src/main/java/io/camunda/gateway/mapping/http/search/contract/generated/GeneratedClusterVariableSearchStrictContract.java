/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml#/components/schemas/ClusterVariableSearchResult
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
public record GeneratedClusterVariableSearchStrictContract(
    @JsonProperty("name") String name,
    @JsonProperty("scope")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableScopeEnum
            scope,
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("value") String value,
    @JsonProperty("isTruncated") Boolean isTruncated) {

  public GeneratedClusterVariableSearchStrictContract {
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(scope, "No scope provided.");
    Objects.requireNonNull(value, "No value provided.");
    Objects.requireNonNull(isTruncated, "No isTruncated provided.");
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements NameStep, ScopeStep, ValueStep, IsTruncatedStep, OptionalStep {
    private String name;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedClusterVariableScopeEnum
        scope;
    private String tenantId;
    private String value;
    private Boolean isTruncated;

    private Builder() {}

    @Override
    public ScopeStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public ValueStep scope(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedClusterVariableScopeEnum
            scope) {
      this.scope = scope;
      return this;
    }

    @Override
    public IsTruncatedStep value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public OptionalStep isTruncated(final Boolean isTruncated) {
      this.isTruncated = isTruncated;
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
    public GeneratedClusterVariableSearchStrictContract build() {
      return new GeneratedClusterVariableSearchStrictContract(
          this.name, this.scope, this.tenantId, this.value, this.isTruncated);
    }
  }

  public interface NameStep {
    ScopeStep name(final String name);
  }

  public interface ScopeStep {
    ValueStep scope(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedClusterVariableScopeEnum
            scope);
  }

  public interface ValueStep {
    IsTruncatedStep value(final String value);
  }

  public interface IsTruncatedStep {
    OptionalStep isTruncated(final Boolean isTruncated);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedClusterVariableSearchStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("ClusterVariableSearchResult", "name");
    public static final ContractPolicy.FieldRef SCOPE =
        ContractPolicy.field("ClusterVariableSearchResult", "scope");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ClusterVariableSearchResult", "tenantId");
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("ClusterVariableSearchResult", "value");
    public static final ContractPolicy.FieldRef IS_TRUNCATED =
        ContractPolicy.field("ClusterVariableSearchResult", "isTruncated");

    private Fields() {}
  }
}
