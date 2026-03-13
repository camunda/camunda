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
public record GeneratedAdvancedIntegerFilterStrictContract(
    @Nullable Integer eq,
    @Nullable Integer neq,
    @Nullable Boolean exists,
    @Nullable Integer gt,
    @Nullable Integer gte,
    @Nullable Integer lt,
    @Nullable Integer lte,
    java.util.@Nullable List<Integer> in) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Integer eq;
    private Integer neq;
    private Boolean exists;
    private Integer gt;
    private Integer gte;
    private Integer lt;
    private Integer lte;
    private java.util.List<Integer> in;

    private Builder() {}

    @Override
    public OptionalStep eq(final @Nullable Integer eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final @Nullable Integer eq, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final @Nullable Integer neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final @Nullable Integer neq, final ContractPolicy.FieldPolicy<Integer> policy) {
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
    public OptionalStep gt(final @Nullable Integer gt) {
      this.gt = gt;
      return this;
    }

    @Override
    public OptionalStep gt(
        final @Nullable Integer gt, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.gt = policy.apply(gt, Fields.GT, null);
      return this;
    }

    @Override
    public OptionalStep gte(final @Nullable Integer gte) {
      this.gte = gte;
      return this;
    }

    @Override
    public OptionalStep gte(
        final @Nullable Integer gte, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.gte = policy.apply(gte, Fields.GTE, null);
      return this;
    }

    @Override
    public OptionalStep lt(final @Nullable Integer lt) {
      this.lt = lt;
      return this;
    }

    @Override
    public OptionalStep lt(
        final @Nullable Integer lt, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.lt = policy.apply(lt, Fields.LT, null);
      return this;
    }

    @Override
    public OptionalStep lte(final @Nullable Integer lte) {
      this.lte = lte;
      return this;
    }

    @Override
    public OptionalStep lte(
        final @Nullable Integer lte, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.lte = policy.apply(lte, Fields.LTE, null);
      return this;
    }

    @Override
    public OptionalStep in(final java.util.@Nullable List<Integer> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.@Nullable List<Integer> in,
        final ContractPolicy.FieldPolicy<java.util.List<Integer>> policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public GeneratedAdvancedIntegerFilterStrictContract build() {
      return new GeneratedAdvancedIntegerFilterStrictContract(
          this.eq, this.neq, this.exists, this.gt, this.gte, this.lt, this.lte, this.in);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final @Nullable Integer eq);

    OptionalStep eq(final @Nullable Integer eq, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep neq(final @Nullable Integer neq);

    OptionalStep neq(final @Nullable Integer neq, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep exists(final @Nullable Boolean exists);

    OptionalStep exists(
        final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep gt(final @Nullable Integer gt);

    OptionalStep gt(final @Nullable Integer gt, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep gte(final @Nullable Integer gte);

    OptionalStep gte(final @Nullable Integer gte, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep lt(final @Nullable Integer lt);

    OptionalStep lt(final @Nullable Integer lt, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep lte(final @Nullable Integer lte);

    OptionalStep lte(final @Nullable Integer lte, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep in(final java.util.@Nullable List<Integer> in);

    OptionalStep in(
        final java.util.@Nullable List<Integer> in,
        final ContractPolicy.FieldPolicy<java.util.List<Integer>> policy);

    GeneratedAdvancedIntegerFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedIntegerFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedIntegerFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedIntegerFilter", "$exists");
    public static final ContractPolicy.FieldRef GT =
        ContractPolicy.field("AdvancedIntegerFilter", "$gt");
    public static final ContractPolicy.FieldRef GTE =
        ContractPolicy.field("AdvancedIntegerFilter", "$gte");
    public static final ContractPolicy.FieldRef LT =
        ContractPolicy.field("AdvancedIntegerFilter", "$lt");
    public static final ContractPolicy.FieldRef LTE =
        ContractPolicy.field("AdvancedIntegerFilter", "$lte");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedIntegerFilter", "$in");

    private Fields() {}
  }
}
