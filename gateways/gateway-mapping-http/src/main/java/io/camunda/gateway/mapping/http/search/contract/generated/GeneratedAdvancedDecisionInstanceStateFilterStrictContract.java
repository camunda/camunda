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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdvancedDecisionInstanceStateFilterStrictContract(
    @Nullable io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq,
    @Nullable io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq,
    @Nullable Boolean exists,
    @Nullable java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in,
    @Nullable java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn,
    @Nullable String like) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq;
    private io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq;
    private Boolean exists;
    private java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in;
    private java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn;
    private String like;

    private Builder() {}

    @Override
    public OptionalStep eq(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy) {
      this.neq = policy.apply(neq, Fields.NEQ, null);
      return this;
    }

    @Override
    public OptionalStep exists(final Boolean exists) {
      this.exists = exists;
      return this;
    }

    @Override
    public OptionalStep exists(
        final Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.exists = policy.apply(exists, Fields.EXISTS, null);
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>>
            policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn) {
      this.notIn = notIn;
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>>
            policy) {
      this.notIn = policy.apply(notIn, Fields.NOT_IN, null);
      return this;
    }

    @Override
    public OptionalStep like(final String like) {
      this.like = like;
      return this;
    }

    @Override
    public OptionalStep like(final String like, final ContractPolicy.FieldPolicy<String> policy) {
      this.like = policy.apply(like, Fields.LIKE, null);
      return this;
    }

    @Override
    public GeneratedAdvancedDecisionInstanceStateFilterStrictContract build() {
      return new GeneratedAdvancedDecisionInstanceStateFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.notIn, this.like);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq);

    OptionalStep eq(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum eq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy);

    OptionalStep neq(final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq);

    OptionalStep neq(
        final io.camunda.gateway.protocol.model.DecisionInstanceStateEnum neq,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>
            policy);

    OptionalStep exists(final Boolean exists);

    OptionalStep exists(final Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in);

    OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>>
            policy);

    OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn);

    OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum> notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.DecisionInstanceStateEnum>>
            policy);

    OptionalStep like(final String like);

    OptionalStep like(final String like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedDecisionInstanceStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$in");
    public static final ContractPolicy.FieldRef NOT_IN =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$notIn");
    public static final ContractPolicy.FieldRef LIKE =
        ContractPolicy.field("AdvancedDecisionInstanceStateFilter", "$like");

    private Fields() {}
  }
}
