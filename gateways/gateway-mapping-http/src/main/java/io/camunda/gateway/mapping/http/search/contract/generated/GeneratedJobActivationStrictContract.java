/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobActivationStrictContract(
    @JsonProperty("jobs") java.util.List<GeneratedActivatedJobStrictContract> jobs) {

  public GeneratedJobActivationStrictContract {
    Objects.requireNonNull(jobs, "No jobs provided.");
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

  public static JobsStep builder() {
    return new Builder();
  }

  public static final class Builder implements JobsStep, OptionalStep {
    private Object jobs;

    private Builder() {}

    @Override
    public OptionalStep jobs(final Object jobs) {
      this.jobs = jobs;
      return this;
    }

    @Override
    public GeneratedJobActivationStrictContract build() {
      return new GeneratedJobActivationStrictContract(coerceJobs(this.jobs));
    }
  }

  public interface JobsStep {
    OptionalStep jobs(final Object jobs);
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
