/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedAdvancedBatchOperationStateFilterStrictContract(
    @JsonProperty("$eq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $eq,
    @JsonProperty("$neq")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $neq,
    @JsonProperty("$exists") @Nullable Boolean $exists,
    @JsonProperty("$in")
        java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            $in,
    @JsonProperty("$like") @Nullable String $like)
    implements GeneratedBatchOperationStateFilterPropertyStrictContract {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedBatchOperationStateEnum
        $eq;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedBatchOperationStateEnum
        $neq;
    private Boolean $exists;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedBatchOperationStateEnum>
        $in;
    private String $like;

    private Builder() {}

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $eq) {
      this.$eq = $eq;
      return this;
    }

    @Override
    public OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            policy) {
      this.$eq = policy.apply($eq, Fields.$EQ, null);
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $neq) {
      this.$neq = $neq;
      return this;
    }

    @Override
    public OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
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
                    .GeneratedBatchOperationStateEnum>
            $in) {
      this.$in = $in;
      return this;
    }

    @Override
    public OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedBatchOperationStateEnum>>
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
    public GeneratedAdvancedBatchOperationStateFilterStrictContract build() {
      return new GeneratedAdvancedBatchOperationStateFilterStrictContract(
          this.$eq, this.$neq, this.$exists, this.$in, this.$like);
    }
  }

  public interface OptionalStep {
    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $eq);

    OptionalStep $eq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            policy);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $neq);

    OptionalStep $neq(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedBatchOperationStateEnum
            $neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            policy);

    OptionalStep $exists(final @Nullable Boolean $exists);

    OptionalStep $exists(
        final @Nullable Boolean $exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            $in);

    OptionalStep $in(
        final java.util.@Nullable List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedBatchOperationStateEnum>
            $in,
        final ContractPolicy.FieldPolicy<
                java.util.List<
                    io.camunda.gateway.mapping.http.search.contract.generated
                        .GeneratedBatchOperationStateEnum>>
            policy);

    OptionalStep $like(final @Nullable String $like);

    OptionalStep $like(
        final @Nullable String $like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedBatchOperationStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef $EQ =
        ContractPolicy.field("AdvancedBatchOperationStateFilter", "$eq");
    public static final ContractPolicy.FieldRef $NEQ =
        ContractPolicy.field("AdvancedBatchOperationStateFilter", "$neq");
    public static final ContractPolicy.FieldRef $EXISTS =
        ContractPolicy.field("AdvancedBatchOperationStateFilter", "$exists");
    public static final ContractPolicy.FieldRef $IN =
        ContractPolicy.field("AdvancedBatchOperationStateFilter", "$in");
    public static final ContractPolicy.FieldRef $LIKE =
        ContractPolicy.field("AdvancedBatchOperationStateFilter", "$like");

    private Fields() {}
  }
}
