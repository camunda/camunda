/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/job-metrics.yaml#/components/schemas/StatusMetric
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedStatusMetricStrictContract(
    @JsonProperty("count") Long count,
    @JsonProperty("lastUpdatedAt") @Nullable String lastUpdatedAt) {

  public GeneratedStatusMetricStrictContract {
    Objects.requireNonNull(count, "No count provided.");
  }

  public static CountStep builder() {
    return new Builder();
  }

  public static final class Builder implements CountStep, OptionalStep {
    private Long count;
    private String lastUpdatedAt;

    private Builder() {}

    @Override
    public OptionalStep count(final Long count) {
      this.count = count;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedAt(final @Nullable String lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedAt(
        final @Nullable String lastUpdatedAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastUpdatedAt = policy.apply(lastUpdatedAt, Fields.LAST_UPDATED_AT, null);
      return this;
    }

    @Override
    public GeneratedStatusMetricStrictContract build() {
      return new GeneratedStatusMetricStrictContract(this.count, this.lastUpdatedAt);
    }
  }

  public interface CountStep {
    OptionalStep count(final Long count);
  }

  public interface OptionalStep {
    OptionalStep lastUpdatedAt(final @Nullable String lastUpdatedAt);

    OptionalStep lastUpdatedAt(
        final @Nullable String lastUpdatedAt, final ContractPolicy.FieldPolicy<String> policy);

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
