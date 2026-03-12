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
public record GeneratedJobChangesetStrictContract(
    @Nullable Integer retries, @Nullable Long timeout) {

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
    private Integer retries;
    private Long timeout;

    private Builder() {}

    @Override
    public OptionalStep retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep timeout(final Long timeout) {
      this.timeout = timeout;
      return this;
    }

    @Override
    public OptionalStep timeout(final Long timeout, final ContractPolicy.FieldPolicy<Long> policy) {
      this.timeout = policy.apply(timeout, Fields.TIMEOUT, null);
      return this;
    }

    @Override
    public GeneratedJobChangesetStrictContract build() {
      return new GeneratedJobChangesetStrictContract(this.retries, this.timeout);
    }
  }

  public interface OptionalStep {
    OptionalStep retries(final Integer retries);

    OptionalStep retries(final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep timeout(final Long timeout);

    OptionalStep timeout(final Long timeout, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedJobChangesetStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("JobChangeset", "retries");
    public static final ContractPolicy.FieldRef TIMEOUT =
        ContractPolicy.field("JobChangeset", "timeout");

    private Fields() {}
  }
}
