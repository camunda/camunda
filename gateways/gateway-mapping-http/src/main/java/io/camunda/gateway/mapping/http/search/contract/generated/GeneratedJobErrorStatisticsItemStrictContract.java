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

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobErrorStatisticsItemStrictContract(
    String errorCode, String errorMessage, Integer workers) {

  public GeneratedJobErrorStatisticsItemStrictContract {
    Objects.requireNonNull(errorCode, "errorCode is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(workers, "workers is required and must not be null");
  }

  public static ErrorCodeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ErrorCodeStep, ErrorMessageStep, WorkersStep, OptionalStep {
    private String errorCode;
    private String errorMessage;
    private Integer workers;

    private Builder() {}

    @Override
    public ErrorMessageStep errorCode(final String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    @Override
    public WorkersStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep workers(final Integer workers) {
      this.workers = workers;
      return this;
    }

    @Override
    public GeneratedJobErrorStatisticsItemStrictContract build() {
      return new GeneratedJobErrorStatisticsItemStrictContract(
          this.errorCode, this.errorMessage, this.workers);
    }
  }

  public interface ErrorCodeStep {
    ErrorMessageStep errorCode(final String errorCode);
  }

  public interface ErrorMessageStep {
    WorkersStep errorMessage(final String errorMessage);
  }

  public interface WorkersStep {
    OptionalStep workers(final Integer workers);
  }

  public interface OptionalStep {
    GeneratedJobErrorStatisticsItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobErrorStatisticsItem", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobErrorStatisticsItem", "errorMessage");
    public static final ContractPolicy.FieldRef WORKERS =
        ContractPolicy.field("JobErrorStatisticsItem", "workers");

    private Fields() {}
  }
}
