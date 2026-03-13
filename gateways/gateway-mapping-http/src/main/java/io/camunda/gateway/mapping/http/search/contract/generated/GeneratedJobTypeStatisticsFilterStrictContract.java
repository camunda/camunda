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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobTypeStatisticsFilterStrictContract(
    String from, String to, @Nullable Object jobType) {

  public GeneratedJobTypeStatisticsFilterStrictContract {
    Objects.requireNonNull(from, "from is required and must not be null");
    Objects.requireNonNull(to, "to is required and must not be null");
  }

  public static FromStep builder() {
    return new Builder();
  }

  public static final class Builder implements FromStep, ToStep, OptionalStep {
    private String from;
    private String to;
    private Object jobType;

    private Builder() {}

    @Override
    public ToStep from(final String from) {
      this.from = from;
      return this;
    }

    @Override
    public OptionalStep to(final String to) {
      this.to = to;
      return this;
    }

    @Override
    public OptionalStep jobType(final @Nullable Object jobType) {
      this.jobType = jobType;
      return this;
    }

    @Override
    public OptionalStep jobType(
        final @Nullable Object jobType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.jobType = policy.apply(jobType, Fields.JOB_TYPE, null);
      return this;
    }

    @Override
    public GeneratedJobTypeStatisticsFilterStrictContract build() {
      return new GeneratedJobTypeStatisticsFilterStrictContract(this.from, this.to, this.jobType);
    }
  }

  public interface FromStep {
    ToStep from(final String from);
  }

  public interface ToStep {
    OptionalStep to(final String to);
  }

  public interface OptionalStep {
    OptionalStep jobType(final @Nullable Object jobType);

    OptionalStep jobType(
        final @Nullable Object jobType, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedJobTypeStatisticsFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FROM =
        ContractPolicy.field("JobTypeStatisticsFilter", "from");
    public static final ContractPolicy.FieldRef TO =
        ContractPolicy.field("JobTypeStatisticsFilter", "to");
    public static final ContractPolicy.FieldRef JOB_TYPE =
        ContractPolicy.field("JobTypeStatisticsFilter", "jobType");

    private Fields() {}
  }
}
