/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml#/components/schemas/AdvancedJobStateFilter
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
public record GeneratedAdvancedJobStateFilterStrictContract(
    io.camunda.gateway.protocol.model.@Nullable JobStateEnum eq,
    io.camunda.gateway.protocol.model.@Nullable JobStateEnum neq,
    @Nullable Boolean exists,
    java.util.@Nullable List<io.camunda.gateway.protocol.model.JobStateEnum> in,
    @Nullable String like
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.protocol.model.JobStateEnum eq;
    private io.camunda.gateway.protocol.model.JobStateEnum neq;
    private Boolean exists;
    private java.util.List<io.camunda.gateway.protocol.model.JobStateEnum> in;
    private String like;

    private Builder() {}

    @Override
    public OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum eq, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }


    @Override
    public OptionalStep neq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum neq, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy) {
      this.neq = policy.apply(neq, Fields.NEQ, null);
      return this;
    }


    @Override
    public OptionalStep exists(final @Nullable Boolean exists) {
      this.exists = exists;
      return this;
    }

    @Override
    public OptionalStep exists(final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.exists = policy.apply(exists, Fields.EXISTS, null);
      return this;
    }


    @Override
    public OptionalStep in(final java.util.@Nullable List<io.camunda.gateway.protocol.model.JobStateEnum> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(final java.util.@Nullable List<io.camunda.gateway.protocol.model.JobStateEnum> in, final ContractPolicy.FieldPolicy<java.util.List<io.camunda.gateway.protocol.model.JobStateEnum>> policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }


    @Override
    public OptionalStep like(final @Nullable String like) {
      this.like = like;
      return this;
    }

    @Override
    public OptionalStep like(final @Nullable String like, final ContractPolicy.FieldPolicy<String> policy) {
      this.like = policy.apply(like, Fields.LIKE, null);
      return this;
    }

    @Override
    public GeneratedAdvancedJobStateFilterStrictContract build() {
      return new GeneratedAdvancedJobStateFilterStrictContract(
          this.eq,
          this.neq,
          this.exists,
          this.in,
          this.like);
    }
  }

  public interface OptionalStep {
  OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum eq);

  OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum eq, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy);


  OptionalStep neq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum neq);

  OptionalStep neq(final io.camunda.gateway.protocol.model.@Nullable JobStateEnum neq, final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.JobStateEnum> policy);


  OptionalStep exists(final @Nullable Boolean exists);

  OptionalStep exists(final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);


  OptionalStep in(final java.util.@Nullable List<io.camunda.gateway.protocol.model.JobStateEnum> in);

  OptionalStep in(final java.util.@Nullable List<io.camunda.gateway.protocol.model.JobStateEnum> in, final ContractPolicy.FieldPolicy<java.util.List<io.camunda.gateway.protocol.model.JobStateEnum>> policy);


  OptionalStep like(final @Nullable String like);

  OptionalStep like(final @Nullable String like, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedAdvancedJobStateFilterStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ = ContractPolicy.field("AdvancedJobStateFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ = ContractPolicy.field("AdvancedJobStateFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS = ContractPolicy.field("AdvancedJobStateFilter", "$exists");
    public static final ContractPolicy.FieldRef IN = ContractPolicy.field("AdvancedJobStateFilter", "$in");
    public static final ContractPolicy.FieldRef LIKE = ContractPolicy.field("AdvancedJobStateFilter", "$like");

    private Fields() {}
  }


}
