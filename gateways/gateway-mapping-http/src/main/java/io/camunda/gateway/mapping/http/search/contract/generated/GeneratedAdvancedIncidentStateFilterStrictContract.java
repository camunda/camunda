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
public record GeneratedAdvancedIncidentStateFilterStrictContract(
    @Nullable io.camunda.gateway.protocol.model.IncidentStateEnum eq,
    @Nullable io.camunda.gateway.protocol.model.IncidentStateEnum neq,
    @Nullable Boolean exists,
    @Nullable java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in,
    @Nullable java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn,
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
    private io.camunda.gateway.protocol.model.IncidentStateEnum eq;
    private io.camunda.gateway.protocol.model.IncidentStateEnum neq;
    private Boolean exists;
    private java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in;
    private java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn;
    private String like;

    private Builder() {}

    @Override
    public OptionalStep eq(final io.camunda.gateway.protocol.model.IncidentStateEnum eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final io.camunda.gateway.protocol.model.IncidentStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
            policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final io.camunda.gateway.protocol.model.IncidentStateEnum neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final io.camunda.gateway.protocol.model.IncidentStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
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
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum>>
            policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn) {
      this.notIn = notIn;
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum>>
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
    public GeneratedAdvancedIncidentStateFilterStrictContract build() {
      return new GeneratedAdvancedIncidentStateFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.notIn, this.like);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final io.camunda.gateway.protocol.model.IncidentStateEnum eq);

    OptionalStep eq(
        final io.camunda.gateway.protocol.model.IncidentStateEnum eq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
            policy);

    OptionalStep neq(final io.camunda.gateway.protocol.model.IncidentStateEnum neq);

    OptionalStep neq(
        final io.camunda.gateway.protocol.model.IncidentStateEnum neq,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.IncidentStateEnum>
            policy);

    OptionalStep exists(final Boolean exists);

    OptionalStep exists(final Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in);

    OptionalStep in(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> in,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum>>
            policy);

    OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn);

    OptionalStep notIn(
        final java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum> notIn,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.IncidentStateEnum>>
            policy);

    OptionalStep like(final String like);

    OptionalStep like(final String like, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAdvancedIncidentStateFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$in");
    public static final ContractPolicy.FieldRef NOT_IN =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$notIn");
    public static final ContractPolicy.FieldRef LIKE =
        ContractPolicy.field("AdvancedIncidentStateFilter", "$like");

    private Fields() {}
  }
}
