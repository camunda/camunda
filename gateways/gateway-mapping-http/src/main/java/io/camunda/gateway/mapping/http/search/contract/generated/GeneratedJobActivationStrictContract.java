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
import java.util.ArrayList;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobActivationStrictContract(
    java.util.List<GeneratedActivatedJobStrictContract> jobs) {

  public GeneratedJobActivationStrictContract {
    Objects.requireNonNull(jobs, "jobs is required and must not be null");
  }

  public static java.util.List<GeneratedActivatedJobStrictContract> coerceJobs(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "jobs must be a List of GeneratedActivatedJobStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedActivatedJobStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedActivatedJobStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "jobs must contain only GeneratedActivatedJobStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static JobsStep builder() {
    return new Builder();
  }

  public static final class Builder implements JobsStep, OptionalStep {
    private Object jobs;
    private ContractPolicy.FieldPolicy<Object> jobsPolicy;

    private Builder() {}

    @Override
    public OptionalStep jobs(final Object jobs, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobs = jobs;
      this.jobsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedJobActivationStrictContract build() {
      return new GeneratedJobActivationStrictContract(
          coerceJobs(applyRequiredPolicy(this.jobs, this.jobsPolicy, Fields.JOBS)));
    }
  }

  public interface JobsStep {
    OptionalStep jobs(final Object jobs, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedJobActivationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef JOBS =
        ContractPolicy.field("JobActivationResult", "jobs");

    private Fields() {}
  }
}
