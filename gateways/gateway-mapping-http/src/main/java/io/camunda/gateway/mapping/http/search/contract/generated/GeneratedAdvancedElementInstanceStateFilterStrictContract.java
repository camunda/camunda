/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedAdvancedElementInstanceStateFilterStrictContract(
    io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum eq,
    io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum neq,
    @Nullable Boolean exists,
    java.util.@Nullable List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum> in,
    @Nullable String like) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.protocol.model.ElementInstanceStateEnum eq;
    private io.camunda.gateway.protocol.model.ElementInstanceStateEnum neq;
    private Boolean exists;
    private java.util.List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum> in;
    private String like;

    private Builder() {}

    @Override
    public OptionalStep eq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
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
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>>
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
    public GeneratedAdvancedElementInstanceStateFilterStrictContract build() {
      return new GeneratedAdvancedElementInstanceStateFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.like);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum eq);

    OptionalStep eq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            policy);

    OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum neq);

    OptionalStep neq(
        final io.camunda.gateway.protocol.model.@Nullable ElementInstanceStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            policy);

    OptionalStep exists(final @Nullable Boolean exists);

    OptionalStep exists(
        final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            in);

    OptionalStep in(
        final java.util.@Nullable List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>
            in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.ElementInstanceStateEnum>>
            policy);

    OptionalStep like(final @Nullable String like);

    OptionalStep like(final @Nullable String like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedElementInstanceStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedElementInstanceStateFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedElementInstanceStateFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedElementInstanceStateFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedElementInstanceStateFilter", "$in");
    public static final ContractPolicy.FieldRef LIKE =
        ContractPolicy.field("AdvancedElementInstanceStateFilter", "$like");

    private Fields() {}
  }
}
