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
public record GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract(
    Integer errorHashCode) {

  public GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract {
    Objects.requireNonNull(errorHashCode, "errorHashCode is required and must not be null");
  }

  public static ErrorHashCodeStep builder() {
    return new Builder();
  }

  public static final class Builder implements ErrorHashCodeStep, OptionalStep {
    private Integer errorHashCode;

    private Builder() {}

    @Override
    public OptionalStep errorHashCode(final Integer errorHashCode) {
      this.errorHashCode = errorHashCode;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract(
          this.errorHashCode);
    }
  }

  public interface ErrorHashCodeStep {
    OptionalStep errorHashCode(final Integer errorHashCode);
  }

  public interface OptionalStep {
    GeneratedIncidentProcessInstanceStatisticsByDefinitionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_HASH_CODE =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionFilter", "errorHashCode");

    private Fields() {}
  }
}
