/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-instances.yaml#/components/schemas/AdvancedDecisionInstanceStateFilter
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
public record GeneratedAdvancedDecisionInstanceStateFilterStrictContract(
    @JsonProperty("$eq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $eq,
    @JsonProperty("$neq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $neq,
    @JsonProperty("$exists") @Nullable Boolean $exists,
    @JsonProperty("$in")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $in,
    @JsonProperty("$notIn")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $notIn,
    @JsonProperty("$like") @Nullable String $like)
    implements GeneratedDecisionInstanceStateFilterPropertyStrictContract {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedDecisionInstanceStateEnum
        $eq;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedDecisionInstanceStateEnum
        $neq;
    private Boolean $exists;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedDecisionInstanceStateEnum>
        $in;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedDecisionInstanceStateEnum>
        $notIn;
    private String $like;

    private Builder() {}

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $eq) {
      this.$eq = $eq;
      return this;
    }

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            policy) {
      this.$eq = policy.apply($eq, Fields.$EQ, null);
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $neq) {
      this.$neq = $neq;
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
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
                    .GeneratedDecisionInstanceStateEnum>
            $in) {
      this.$in = $in;
      return this;
    }

    @Override
    public OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedDecisionInstanceStateEnum>>
            policy) {
      this.$in = policy.apply($in, Fields.$IN, null);
      return this;
    }

    @Override
    public OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $notIn) {
      this.$notIn = $notIn;
      return this;
    }

    @Override
    public OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedDecisionInstanceStateEnum>>
            policy) {
      this.$notIn = policy.apply($notIn, Fields.$NOT_IN, null);
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
    public GeneratedAdvancedDecisionInstanceStateFilterStrictContract build() {
      return new GeneratedAdvancedDecisionInstanceStateFilterStrictContract(
          this.$eq, this.$neq, this.$exists, this.$in, this.$notIn, this.$like);
    }
  }

  public interface OptionalStep {
    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $eq);

    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            policy);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $neq);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedDecisionInstanceStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            policy);

    OptionalStep $exists(final @Nullable Boolean $exists);

    OptionalStep $exists(
        final @Nullable Boolean $exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $in);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedDecisionInstanceStateEnum>>
            policy);

    OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $notIn);

    OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedDecisionInstanceStateEnum>
            $notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedDecisionInstanceStateEnum>>
            policy);

    OptionalStep $like(final @Nullable String $like);

    OptionalStep $like(
        final @Nullable String $like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedDecisionInstanceStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef $EQ =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$eq");
    public static final ContractPolicy.FieldRef $NEQ =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$neq");
    public static final ContractPolicy.FieldRef $EXISTS =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$exists");
    public static final ContractPolicy.FieldRef $IN =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$in");
    public static final ContractPolicy.FieldRef $NOT_IN =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$notIn");
    public static final ContractPolicy.FieldRef $LIKE =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$like");

    private Fields() {}
  }
}
