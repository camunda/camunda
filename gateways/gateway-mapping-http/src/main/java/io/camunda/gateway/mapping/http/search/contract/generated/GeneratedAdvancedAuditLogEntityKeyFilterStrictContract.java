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
public record GeneratedAdvancedAuditLogEntityKeyFilterStrictContract(
    @Nullable String eq,
    @Nullable String neq,
    @Nullable Boolean exists,
    java.util.@Nullable List<String> in,
    java.util.@Nullable List<String> notIn) {

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
    public OptionalStep eq(final @Nullable String eq) {
      this.eq = eq;
      return this;
    }

    @Override
    public OptionalStep eq(
        final @Nullable String eq, final ContractPolicy.FieldPolicy<String> policy) {
      this.eq = policy.apply(eq, Fields.EQ, null);
      return this;
    }

    @Override
    public OptionalStep neq(final @Nullable String neq) {
      this.neq = neq;
      return this;
    }

    @Override
    public OptionalStep neq(
        final @Nullable String neq, final ContractPolicy.FieldPolicy<String> policy) {
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
    public GeneratedAdvancedAuditLogEntityKeyFilterStrictContract build() {
      return new GeneratedAdvancedAuditLogEntityKeyFilterStrictContract(
          this.eq, this.neq, this.exists, this.in, this.notIn);
    }
  }

  public interface OptionalStep {
    OptionalStep eq(final @Nullable String eq);

    OptionalStep eq(final @Nullable String eq, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep neq(final @Nullable String neq);

    OptionalStep neq(final @Nullable String neq, final ContractPolicy.FieldPolicy<String> policy);

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

    GeneratedAdvancedAuditLogEntityKeyFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EQ =
        ContractPolicy.field("AdvancedAuditLogEntityKeyFilter", "$eq");
    public static final ContractPolicy.FieldRef NEQ =
        ContractPolicy.field("AdvancedAuditLogEntityKeyFilter", "$neq");
    public static final ContractPolicy.FieldRef EXISTS =
        ContractPolicy.field("AdvancedAuditLogEntityKeyFilter", "$exists");
    public static final ContractPolicy.FieldRef IN =
        ContractPolicy.field("AdvancedAuditLogEntityKeyFilter", "$in");
    public static final ContractPolicy.FieldRef NOT_IN =
        ContractPolicy.field("AdvancedAuditLogEntityKeyFilter", "$notIn");

    private Fields() {}
  }
}
