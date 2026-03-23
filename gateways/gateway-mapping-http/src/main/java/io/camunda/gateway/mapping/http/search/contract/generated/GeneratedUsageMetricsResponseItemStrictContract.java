/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/system.yaml#/components/schemas/UsageMetricsResponseItem
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUsageMetricsResponseItemStrictContract(
    @JsonProperty("processInstances") Long processInstances,
    @JsonProperty("decisionInstances") Long decisionInstances,
    @JsonProperty("assignees") Long assignees) {

  public GeneratedUsageMetricsResponseItemStrictContract {
    Objects.requireNonNull(processInstances, "No processInstances provided.");
    Objects.requireNonNull(decisionInstances, "No decisionInstances provided.");
    Objects.requireNonNull(assignees, "No assignees provided.");
  }

  public static ProcessInstancesStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessInstancesStep, DecisionInstancesStep, AssigneesStep, OptionalStep {
    private Long processInstances;
    private Long decisionInstances;
    private Long assignees;

    private Builder() {}

    @Override
    public DecisionInstancesStep processInstances(final Long processInstances) {
      this.processInstances = processInstances;
      return this;
    }

    @Override
    public AssigneesStep decisionInstances(final Long decisionInstances) {
      this.decisionInstances = decisionInstances;
      return this;
    }

    @Override
    public OptionalStep assignees(final Long assignees) {
      this.assignees = assignees;
      return this;
    }

    @Override
    public GeneratedUsageMetricsResponseItemStrictContract build() {
      return new GeneratedUsageMetricsResponseItemStrictContract(
          this.processInstances, this.decisionInstances, this.assignees);
    }
  }

  public interface ProcessInstancesStep {
    DecisionInstancesStep processInstances(final Long processInstances);
  }

  public interface DecisionInstancesStep {
    AssigneesStep decisionInstances(final Long decisionInstances);
  }

  public interface AssigneesStep {
    OptionalStep assignees(final Long assignees);
  }

  public interface OptionalStep {
    GeneratedUsageMetricsResponseItemStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_INSTANCES =
        ContractPolicy.field("UsageMetricsResponseItem", "processInstances");
    public static final ContractPolicy.FieldRef DECISION_INSTANCES =
        ContractPolicy.field("UsageMetricsResponseItem", "decisionInstances");
    public static final ContractPolicy.FieldRef ASSIGNEES =
        ContractPolicy.field("UsageMetricsResponseItem", "assignees");

    private Fields() {}
  }
}
