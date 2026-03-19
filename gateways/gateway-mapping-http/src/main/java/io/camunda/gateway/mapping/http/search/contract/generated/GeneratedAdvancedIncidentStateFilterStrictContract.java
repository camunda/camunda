/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/incidents.yaml#/components/schemas/AdvancedIncidentStateFilter
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
public record GeneratedAdvancedIncidentStateFilterStrictContract(
    @JsonProperty("$eq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $eq,
    @JsonProperty("$neq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $neq,
    @JsonProperty("$exists") @Nullable Boolean $exists,
    @JsonProperty("$in")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $in,
    @JsonProperty("$notIn")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $notIn,
    @JsonProperty("$like") @Nullable String $like)
    implements GeneratedIncidentStateFilterPropertyStrictContract {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStateEnum
        $eq;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStateEnum
        $neq;
    private Boolean $exists;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStateEnum>
        $in;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStateEnum>
        $notIn;
    private String $like;

    private Builder() {}

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $eq) {
      this.$eq = $eq;
      return this;
    }

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            policy) {
      this.$eq = policy.apply($eq, Fields.$EQ, null);
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $neq) {
      this.$neq = $neq;
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
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
                    .GeneratedIncidentStateEnum>
            $in) {
      this.$in = $in;
      return this;
    }

    @Override
    public OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedIncidentStateEnum>>
            policy) {
      this.$in = policy.apply($in, Fields.$IN, null);
      return this;
    }

    @Override
    public OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $notIn) {
      this.$notIn = $notIn;
      return this;
    }

    @Override
    public OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedIncidentStateEnum>>
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
    public GeneratedAdvancedIncidentStateFilterStrictContract build() {
      return new GeneratedAdvancedIncidentStateFilterStrictContract(
          this.$eq, this.$neq, this.$exists, this.$in, this.$notIn, this.$like);
    }
  }

  public interface OptionalStep {
    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $eq);

    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            policy);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $neq);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedIncidentStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            policy);

    OptionalStep $exists(final @Nullable Boolean $exists);

    OptionalStep $exists(
        final @Nullable Boolean $exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $in);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedIncidentStateEnum>>
            policy);

    OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $notIn);

    OptionalStep $notIn(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedIncidentStateEnum>
            $notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedIncidentStateEnum>>
            policy);

    OptionalStep $like(final @Nullable String $like);

    OptionalStep $like(
        final @Nullable String $like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedIncidentStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef $EQ =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$eq");
    public static final ContractPolicy.FieldRef $NEQ =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$neq");
    public static final ContractPolicy.FieldRef $EXISTS =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$exists");
    public static final ContractPolicy.FieldRef $IN =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$in");
    public static final ContractPolicy.FieldRef $NOT_IN =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$notIn");
    public static final ContractPolicy.FieldRef $LIKE =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$like");

    private Fields() {}
  }
}
