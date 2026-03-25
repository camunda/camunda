/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml#/components/schemas/ClusterVariableSearchQueryFilterRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedClusterVariableSearchQueryFilterRequestStrictContract(
    @Nullable Object name,
    @Nullable Object value,
    @Nullable Object scope,
    @Nullable Object tenantId,
    @Nullable Boolean isTruncated
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object name;
    private Object value;
    private Object scope;
    private Object tenantId;
    private Boolean isTruncated;

    private Builder() {}

    @Override
    public OptionalStep name(final @Nullable Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }


    @Override
    public OptionalStep value(final @Nullable Object value) {
      this.value = value;
      return this;
    }

    @Override
    public OptionalStep value(final @Nullable Object value, final ContractPolicy.FieldPolicy<Object> policy) {
      this.value = policy.apply(value, Fields.VALUE, null);
      return this;
    }


    @Override
    public OptionalStep scope(final @Nullable Object scope) {
      this.scope = scope;
      return this;
    }

    @Override
    public OptionalStep scope(final @Nullable Object scope, final ContractPolicy.FieldPolicy<Object> policy) {
      this.scope = policy.apply(scope, Fields.SCOPE, null);
      return this;
    }


    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }


    @Override
    public OptionalStep isTruncated(final @Nullable Boolean isTruncated) {
      this.isTruncated = isTruncated;
      return this;
    }

    @Override
    public OptionalStep isTruncated(final @Nullable Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.isTruncated = policy.apply(isTruncated, Fields.IS_TRUNCATED, null);
      return this;
    }

    @Override
    public GeneratedClusterVariableSearchQueryFilterRequestStrictContract build() {
      return new GeneratedClusterVariableSearchQueryFilterRequestStrictContract(
          this.name,
          this.value,
          this.scope,
          this.tenantId,
          this.isTruncated);
    }
  }

  public interface OptionalStep {
  OptionalStep name(final @Nullable Object name);

  OptionalStep name(final @Nullable Object name, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep value(final @Nullable Object value);

  OptionalStep value(final @Nullable Object value, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep scope(final @Nullable Object scope);

  OptionalStep scope(final @Nullable Object scope, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep tenantId(final @Nullable Object tenantId);

  OptionalStep tenantId(final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep isTruncated(final @Nullable Boolean isTruncated);

  OptionalStep isTruncated(final @Nullable Boolean isTruncated, final ContractPolicy.FieldPolicy<Boolean> policy);


    GeneratedClusterVariableSearchQueryFilterRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("ClusterVariableSearchQueryFilterRequest", "name");
    public static final ContractPolicy.FieldRef VALUE = ContractPolicy.field("ClusterVariableSearchQueryFilterRequest", "value");
    public static final ContractPolicy.FieldRef SCOPE = ContractPolicy.field("ClusterVariableSearchQueryFilterRequest", "scope");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ClusterVariableSearchQueryFilterRequest", "tenantId");
    public static final ContractPolicy.FieldRef IS_TRUNCATED = ContractPolicy.field("ClusterVariableSearchQueryFilterRequest", "isTruncated");

    private Fields() {}
  }


}
