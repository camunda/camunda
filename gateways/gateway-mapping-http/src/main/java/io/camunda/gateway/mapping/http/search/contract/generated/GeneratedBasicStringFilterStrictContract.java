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
public record GeneratedBasicStringFilterStrictContract(
    @Nullable String eq,
    @Nullable String neq,
    @Nullable Boolean exists,
    @Nullable java.util.List<String> in,
    @Nullable java.util.List<String> notIn) {

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
    private String eq;
    private String neq;
    private Boolean exists;
    private java.util.List<String> in;
    private java.util.List<String> notIn;

    private Builder() {}

    @Override
    public OptionalStep eq(final String eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(final String eq, final ContractPolicy.FieldPolicy<String> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final String neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(final String neq, final ContractPolicy.FieldPolicy<String> policy) {
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
    public OptionalStep in(final java.util.List<String> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.List<String> in,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public OptionalStep notIn(final java.util.List<String> notIn) {
      this.notIn = notIn;
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.List<String> notIn,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.notIn = policy.apply(notIn, Fields.NOT_IN, null);
      return this;
    }

    @Override
    public GeneratedBasicStringFilterStrictContract build() {
      return new GeneratedBasicStringFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.notIn);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final String eq);

    OptionalStep eq(final String eq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep neq(final String neq);

    OptionalStep neq(final String neq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep exists(final Boolean exists);

    OptionalStep exists(final Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(final java.util.List<String> in);

    OptionalStep in(
        final java.util.List<String> in,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep notIn(final java.util.List<String> notIn);

    OptionalStep notIn(
        final java.util.List<String> notIn,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    GeneratedBasicStringFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("BasicStringFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("BasicStringFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("BasicStringFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("BasicStringFilter", "$in");
    public static final ContractPolicy.FieldRef NOT_IN =
        ContractPolicy.field("BasicStringFilter", "$notIn");

    private Fields() {}
  }
}
