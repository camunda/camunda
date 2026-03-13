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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdvancedUserTaskStateFilterStrictContract(
    io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum eq,
    io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum neq,
    @Nullable Boolean exists,
    java.util.@Nullable List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in,
    @Nullable String like) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.protocol.model.UserTaskStateEnum eq;
    private io.camunda.gateway.protocol.model.UserTaskStateEnum neq;
    private Boolean exists;
    private java.util.List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in;
    private String like;

    private Builder() {}

    @Override
    public OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy) {
      this.neq = policy.apply(neq, Fields.NEQ, null);
      return this;
    }

    @Override
    public OptionalStep exists(final @Nullable Boolean exists) {
      this.exists = exists;
      return this;
    }

    @Override
    public OptionalStep exists(
        final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.exists = policy.apply(exists, Fields.EXISTS, null);
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.UserTaskStateEnum>>
            policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public OptionalStep like(final @Nullable String like) {
      this.like = like;
      return this;
    }

    @Override
    public OptionalStep like(
        final @Nullable String like, final ContractPolicy.FieldPolicy<String> policy) {
      this.like = policy.apply(like, Fields.LIKE, null);
      return this;
    }

    @Override
    public GeneratedAdvancedUserTaskStateFilterStrictContract build() {
      return new GeneratedAdvancedUserTaskStateFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.like);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum eq);

    OptionalStep eq(
        final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy);

    OptionalStep neq(final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum neq);

    OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable UserTaskStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.UserTaskStateEnum>
            policy);

    OptionalStep exists(final @Nullable Boolean exists);

    OptionalStep exists(
        final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in);

    OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.UserTaskStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.UserTaskStateEnum>>
            policy);

    OptionalStep like(final @Nullable String like);

    OptionalStep like(final @Nullable String like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedUserTaskStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedUserTaskStateFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedUserTaskStateFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedUserTaskStateFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedUserTaskStateFilter", "$in");
    public static final ContractPolicy.FieldRef LIKE =
        ContractPolicy.field("AdvancedUserTaskStateFilter", "$like");

    private Fields() {}
  }
}
