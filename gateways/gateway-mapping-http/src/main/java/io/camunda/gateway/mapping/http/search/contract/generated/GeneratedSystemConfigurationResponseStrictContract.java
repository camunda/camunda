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
import org.jspecify.annotations.NullMarked;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedSystemConfigurationResponseStrictContract(
    GeneratedJobMetricsConfigurationResponseStrictContract jobMetrics) {

  public GeneratedSystemConfigurationResponseStrictContract {
    Objects.requireNonNull(jobMetrics, "jobMetrics is required and must not be null");
  }

  public static GeneratedJobMetricsConfigurationResponseStrictContract coerceJobMetrics(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedJobMetricsConfigurationResponseStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "jobMetrics must be a GeneratedJobMetricsConfigurationResponseStrictContract, but was "
            + value.getClass().getName());
  }

  public static JobMetricsStep builder() {
    return new Builder();
  }

  public static final class Builder implements JobMetricsStep, OptionalStep {
    private Object jobMetrics;

    private Builder() {}

    @Override
    public OptionalStep jobMetrics(final Object jobMetrics) {
      this.jobMetrics = jobMetrics;
      return this;
    }

    @Override
    public GeneratedSystemConfigurationResponseStrictContract build() {
      return new GeneratedSystemConfigurationResponseStrictContract(
          coerceJobMetrics(this.jobMetrics));
    }
  }

  public interface JobMetricsStep {
    OptionalStep jobMetrics(final Object jobMetrics);
  }

  public interface OptionalStep {
    GeneratedSystemConfigurationResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef JOB_METRICS =
        ContractPolicy.field("SystemConfigurationResponse", "jobMetrics");

    private Fields() {}
  }
}
