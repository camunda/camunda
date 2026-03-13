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
public record GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract(
    Integer errorHashCode, String errorMessage, Long activeInstancesWithErrorCount) {

  public GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract {
    Objects.requireNonNull(errorHashCode, "errorHashCode is required and must not be null");
    Objects.requireNonNull(errorMessage, "errorMessage is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithErrorCount,
        "activeInstancesWithErrorCount is required and must not be null");
  }

  public static ErrorHashCodeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ErrorHashCodeStep,
          ErrorMessageStep,
          ActiveInstancesWithErrorCountStep,
          OptionalStep {
    private Integer errorHashCode;
    private String errorMessage;
    private Long activeInstancesWithErrorCount;

    private Builder() {}

    @Override
    public ErrorMessageStep errorHashCode(final Integer errorHashCode) {
      this.errorHashCode = errorHashCode;
      return this;
    }

    @Override
    public ActiveInstancesWithErrorCountStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithErrorCount(final Long activeInstancesWithErrorCount) {
      this.activeInstancesWithErrorCount = activeInstancesWithErrorCount;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract(
          this.errorHashCode, this.errorMessage, this.activeInstancesWithErrorCount);
    }
  }

  public interface ErrorHashCodeStep {
    ErrorMessageStep errorHashCode(final Integer errorHashCode);
  }

  public interface ErrorMessageStep {
    ActiveInstancesWithErrorCountStep errorMessage(final String errorMessage);
  }

  public interface ActiveInstancesWithErrorCountStep {
    OptionalStep activeInstancesWithErrorCount(final Long activeInstancesWithErrorCount);
  }

  public interface OptionalStep {
    GeneratedIncidentProcessInstanceStatisticsByErrorStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_HASH_CODE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorResult", "errorHashCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByErrorResult", "errorMessage");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_ERROR_COUNT =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByErrorResult", "activeInstancesWithErrorCount");

    private Fields() {}
  }
}
