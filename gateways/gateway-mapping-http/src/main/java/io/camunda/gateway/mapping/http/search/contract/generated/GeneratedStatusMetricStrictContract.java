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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedStatusMetricStrictContract(Long count, @Nullable String lastUpdatedAt) {

  public GeneratedStatusMetricStrictContract {
    Objects.requireNonNull(count, "count is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static CountStep builder() {
    return new Builder();
  }

  public static final class Builder implements CountStep, OptionalStep {
    private Long count;
    private ContractPolicy.FieldPolicy<Long> countPolicy;
    private String lastUpdatedAt;

    private Builder() {}

    @Override
    public OptionalStep count(final Long count, final ContractPolicy.FieldPolicy<Long> policy) {
      this.count = count;
      this.countPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedAt(final String lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedAt(
        final String lastUpdatedAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastUpdatedAt = policy.apply(lastUpdatedAt, Fields.LAST_UPDATED_AT, null);
      return this;
    }

    @Override
    public GeneratedStatusMetricStrictContract build() {
      return new GeneratedStatusMetricStrictContract(
          applyRequiredPolicy(this.count, this.countPolicy, Fields.COUNT), this.lastUpdatedAt);
    }
  }

  public interface CountStep {
    OptionalStep count(final Long count, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    OptionalStep lastUpdatedAt(final String lastUpdatedAt);

    OptionalStep lastUpdatedAt(
        final String lastUpdatedAt, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedStatusMetricStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef COUNT =
        ContractPolicy.field("StatusMetric", "count");
    public static final ContractPolicy.FieldRef LAST_UPDATED_AT =
        ContractPolicy.field("StatusMetric", "lastUpdatedAt");

    private Fields() {}
  }
}
