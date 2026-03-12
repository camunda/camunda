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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedClockPinRequestStrictContract(Long timestamp) {

  public GeneratedClockPinRequestStrictContract {
    Objects.requireNonNull(timestamp, "timestamp is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TimestampStep builder() {
    return new Builder();
  }

  public static final class Builder implements TimestampStep, OptionalStep {
    private Long timestamp;
    private ContractPolicy.FieldPolicy<Long> timestampPolicy;

    private Builder() {}

    @Override
    public OptionalStep timestamp(
        final Long timestamp, final ContractPolicy.FieldPolicy<Long> policy) {
      this.timestamp = timestamp;
      this.timestampPolicy = policy;
      return this;
    }

    @Override
    public GeneratedClockPinRequestStrictContract build() {
      return new GeneratedClockPinRequestStrictContract(
          applyRequiredPolicy(this.timestamp, this.timestampPolicy, Fields.TIMESTAMP));
    }
  }

  public interface TimestampStep {
    OptionalStep timestamp(final Long timestamp, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    GeneratedClockPinRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TIMESTAMP =
        ContractPolicy.field("ClockPinRequest", "timestamp");

    private Fields() {}
  }
}
