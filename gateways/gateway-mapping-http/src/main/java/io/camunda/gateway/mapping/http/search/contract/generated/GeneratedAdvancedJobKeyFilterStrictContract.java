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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAdvancedJobKeyFilterStrictContract(
    @Nullable String eq,
    @Nullable String neq,
    @Nullable Boolean exists,
    java.util.@Nullable List<String> in,
    java.util.@Nullable List<String> notIn) {

  public static String coerceEq(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "$eq must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceNeq(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "$neq must be a String or Number, but was " + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object eq;
    private Object neq;
    private Boolean exists;
    private java.util.List<String> in;
    private java.util.List<String> notIn;

    private Builder() {}

    @Override
    public OptionalStep eq(final @Nullable String eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(final @Nullable Object eq) {
      this.eq = eq;
      return this;
    }

    public Builder eq(final @Nullable String eq, final ContractPolicy.FieldPolicy<String> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep eq(
        final @Nullable Object eq, final ContractPolicy.FieldPolicy<Object> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final @Nullable String neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(final @Nullable Object neq) {
      this.neq = neq;
      return this;
    }

    public Builder neq(
        final @Nullable String neq, final ContractPolicy.FieldPolicy<String> policy) {
      this.neq = policy.apply(neq, Fields.NEQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(
        final @Nullable Object neq, final ContractPolicy.FieldPolicy<Object> policy) {
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
    public OptionalStep in(final java.util.@Nullable List<String> in) {
      this.in = in;
      return this;
    }

    @Override
    public OptionalStep in(
        final java.util.@Nullable List<String> in,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.in = policy.apply(in, Fields.IN, null);
      return this;
    }

    @Override
    public OptionalStep notIn(final java.util.@Nullable List<String> notIn) {
      this.notIn = notIn;
      return this;
    }

    @Override
    public OptionalStep notIn(
        final java.util.@Nullable List<String> notIn,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.notIn = policy.apply(notIn, Fields.NOT_IN, null);
      return this;
    }

    @Override
    public GeneratedAdvancedJobKeyFilterStrictContract build() {
      return new GeneratedAdvancedJobKeyFilterStrictContract(
          coerceEq(this.eq), coerceNeq(this.neq), this.exists, this.in, this.notIn);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final @Nullable String eq);

    OptionalStep eq(final @Nullable Object eq);

    OptionalStep eq(final @Nullable String eq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep eq(final @Nullable Object eq, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep neq(final @Nullable String neq);

    OptionalStep neq(final @Nullable Object neq);

    OptionalStep neq(final @Nullable String neq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep neq(final @Nullable Object neq, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep exists(final @Nullable Boolean exists);

    OptionalStep exists(
        final @Nullable Boolean exists, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep in(final java.util.@Nullable List<String> in);

    OptionalStep in(
        final java.util.@Nullable List<String> in,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep notIn(final java.util.@Nullable List<String> notIn);

    OptionalStep notIn(
        final java.util.@Nullable List<String> notIn,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    GeneratedAdvancedJobKeyFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedJobKeyFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedJobKeyFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedJobKeyFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedJobKeyFilter", "$in");
    public static final ContractPolicy.FieldRef NOT_IN =
        ContractPolicy.field("AdvancedJobKeyFilter", "$notIn");

    private Fields() {}
  }
}
