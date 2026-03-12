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
public record GeneratedAdvancedDateTimeFilterStrictContract(
    @Nullable String eq,
    @Nullable String neq,
    @Nullable Boolean exists,
    @Nullable String gt,
    @Nullable String gte,
    @Nullable String lt,
    @Nullable String lte,
    @Nullable java.util.List<String> in) {

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
    private String gt;
    private String gte;
    private String lt;
    private String lte;
    private java.util.List<String> in;

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
    public OptionalStep gt(final String gt) {
      this.gt = gt;
      return this;
    }

    @Override
    public OptionalStep gt(final String gt, final ContractPolicy.FieldPolicy<String> policy) {
      this.gt = policy.apply(gt, Fields.GT, null);
      return this;
    }

    @Override
    public OptionalStep gte(final String gte) {
      this.gte = gte;
      return this;
    }

    @Override
    public OptionalStep gte(final String gte, final ContractPolicy.FieldPolicy<String> policy) {
      this.gte = policy.apply(gte, Fields.GTE, null);
      return this;
    }

    @Override
    public OptionalStep lt(final String lt) {
      this.lt = lt;
      return this;
    }

    @Override
    public OptionalStep lt(final String lt, final ContractPolicy.FieldPolicy<String> policy) {
      this.lt = policy.apply(lt, Fields.LT, null);
      return this;
    }

    @Override
    public OptionalStep lte(final String lte) {
      this.lte = lte;
      return this;
    }

    @Override
    public OptionalStep lte(final String lte, final ContractPolicy.FieldPolicy<String> policy) {
      this.lte = policy.apply(lte, Fields.LTE, null);
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
    public GeneratedAdvancedDateTimeFilterStrictContract build() {
      return new GeneratedAdvancedDateTimeFilterStrictContract(
          this.eq, this.neq, this.exists, this.gt, this.gte, this.lt, this.lte, this.in);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final String eq);

    OptionalStep eq(final String eq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep neq(final String neq);

    OptionalStep neq(final String neq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep exists(final Boolean exists);

    OptionalStep exists(final Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep gt(final String gt);

    OptionalStep gt(final String gt, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep gte(final String gte);

    OptionalStep gte(final String gte, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep lt(final String lt);

    OptionalStep lt(final String lt, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep lte(final String lte);

    OptionalStep lte(final String lte, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep in(final java.util.List<String> in);

    OptionalStep in(
        final java.util.List<String> in,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    GeneratedAdvancedDateTimeFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedDateTimeFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedDateTimeFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedDateTimeFilter", "$exists");
    public static final ContractPolicy.FieldRef GT =
        ContractPolicy.field("AdvancedDateTimeFilter", "$gt");
    public static final ContractPolicy.FieldRef GTE =
        ContractPolicy.field("AdvancedDateTimeFilter", "$gte");
    public static final ContractPolicy.FieldRef LT =
        ContractPolicy.field("AdvancedDateTimeFilter", "$lt");
    public static final ContractPolicy.FieldRef LTE =
        ContractPolicy.field("AdvancedDateTimeFilter", "$lte");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedDateTimeFilter", "$in");

    private Fields() {}
  }
}
