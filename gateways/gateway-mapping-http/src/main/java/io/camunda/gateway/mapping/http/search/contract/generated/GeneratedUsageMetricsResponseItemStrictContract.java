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
public record GeneratedUsageMetricsResponseItemStrictContract(
    Long processInstances, Long decisionInstances, Long assignees) {

  public GeneratedUsageMetricsResponseItemStrictContract {
    Objects.requireNonNull(processInstances, "processInstances is required and must not be null");
    Objects.requireNonNull(decisionInstances, "decisionInstances is required and must not be null");
    Objects.requireNonNull(assignees, "assignees is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ProcessInstancesStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessInstancesStep, DecisionInstancesStep, AssigneesStep, OptionalStep {
    private Long processInstances;
    private ContractPolicy.FieldPolicy<Long> processInstancesPolicy;
    private Long decisionInstances;
    private ContractPolicy.FieldPolicy<Long> decisionInstancesPolicy;
    private Long assignees;
    private ContractPolicy.FieldPolicy<Long> assigneesPolicy;

    private Builder() {}

    @Override
    public DecisionInstancesStep processInstances(
        final Long processInstances, final ContractPolicy.FieldPolicy<Long> policy) {
      this.processInstances = processInstances;
      this.processInstancesPolicy = policy;
      return this;
    }

    @Override
    public AssigneesStep decisionInstances(
        final Long decisionInstances, final ContractPolicy.FieldPolicy<Long> policy) {
      this.decisionInstances = decisionInstances;
      this.decisionInstancesPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep assignees(
        final Long assignees, final ContractPolicy.FieldPolicy<Long> policy) {
      this.assignees = assignees;
      this.assigneesPolicy = policy;
      return this;
    }

    @Override
    public GeneratedUsageMetricsResponseItemStrictContract build() {
      return new GeneratedUsageMetricsResponseItemStrictContract(
          applyRequiredPolicy(
              this.processInstances, this.processInstancesPolicy, Fields.PROCESS_INSTANCES),
          applyRequiredPolicy(
              this.decisionInstances, this.decisionInstancesPolicy, Fields.DECISION_INSTANCES),
          applyRequiredPolicy(this.assignees, this.assigneesPolicy, Fields.ASSIGNEES));
    }
  }

  public interface ProcessInstancesStep {
    DecisionInstancesStep processInstances(
        final Long processInstances, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface DecisionInstancesStep {
    AssigneesStep decisionInstances(
        final Long decisionInstances, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface AssigneesStep {
    OptionalStep assignees(final Long assignees, final ContractPolicy.FieldPolicy<Long> policy);
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
