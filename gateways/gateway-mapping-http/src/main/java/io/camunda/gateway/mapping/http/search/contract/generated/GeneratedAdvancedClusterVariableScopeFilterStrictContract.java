/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml#/components/schemas/AdvancedClusterVariableScopeFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdvancedClusterVariableScopeFilterStrictContract(
    @JsonProperty("$eq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $eq,
    @JsonProperty("$neq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $neq,
    @JsonProperty("$exists") @Nullable Boolean $exists,
    @JsonProperty("$in")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            $in,
    @JsonProperty("$like") @Nullable String $like)
    implements GeneratedClusterVariableScopeFilterPropertyStrictContract {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedClusterVariableScopeEnum
        $eq;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedClusterVariableScopeEnum
        $neq;
    private Boolean $exists;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedClusterVariableScopeEnum>
        $in;
    private String $like;

    private Builder() {}

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $eq) {
      this.$eq = $eq;
      return this;
    }

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            policy) {
      this.$eq = policy.apply($eq, Fields.$EQ, null);
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $neq) {
      this.$neq = $neq;
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            policy) {
      this.$neq = policy.apply($neq, Fields.$NEQ, null);
      return this;
    }

    @Override
    public OptionalStep $exists(final @Nullable Boolean $exists) {
      this.$exists = $exists;
      return this;
    }

    @Override
    public OptionalStep $exists(
        final @Nullable Boolean $exists, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.$exists = policy.apply($exists, Fields.$EXISTS, null);
      return this;
    }

    @Override
    public OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            $in) {
      this.$in = $in;
      return this;
    }

    @Override
    public OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedClusterVariableScopeEnum>>
            policy) {
      this.$in = policy.apply($in, Fields.$IN, null);
      return this;
    }

    @Override
    public OptionalStep $like(final @Nullable String $like) {
      this.$like = $like;
      return this;
    }

    @Override
    public OptionalStep $like(
        final @Nullable String $like, final ContractPolicy.FieldPolicy<String> policy) {
      this.$like = policy.apply($like, Fields.$LIKE, null);
      return this;
    }

    @Override
    public GeneratedAdvancedClusterVariableScopeFilterStrictContract build() {
      return new GeneratedAdvancedClusterVariableScopeFilterStrictContract(
          this.$eq, this.$neq, this.$exists, this.$in, this.$like);
    }
  }

  public interface OptionalStep {
    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $eq);

    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            policy);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $neq);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedClusterVariableScopeEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            policy);

    OptionalStep $exists(final @Nullable Boolean $exists);

    OptionalStep $exists(
        final @Nullable Boolean $exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            $in);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedClusterVariableScopeEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedClusterVariableScopeEnum>>
            policy);

    OptionalStep $like(final @Nullable String $like);

    OptionalStep $like(
        final @Nullable String $like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedClusterVariableScopeFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef $EQ =
        ContractPolicy.field("AdvancedClusterVariableScopeFilter", "$eq");
    public static final ContractPolicy.FieldRef $NEQ =
        ContractPolicy.field("AdvancedClusterVariableScopeFilter", "$neq");
    public static final ContractPolicy.FieldRef $EXISTS =
        ContractPolicy.field("AdvancedClusterVariableScopeFilter", "$exists");
    public static final ContractPolicy.FieldRef $IN =
        ContractPolicy.field("AdvancedClusterVariableScopeFilter", "$in");
    public static final ContractPolicy.FieldRef $LIKE =
        ContractPolicy.field("AdvancedClusterVariableScopeFilter", "$like");

    private Fields() {}
  }
}
