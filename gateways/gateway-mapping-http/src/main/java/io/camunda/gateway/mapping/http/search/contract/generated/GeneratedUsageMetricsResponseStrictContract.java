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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUsageMetricsResponseStrictContract(
    @JsonProperty("processInstances") Long processInstances,
    @JsonProperty("decisionInstances") Long decisionInstances,
    @JsonProperty("assignees") Long assignees,
    @JsonProperty("activeTenants") Long activeTenants,
    @JsonProperty("tenants")
        java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants) {

  public GeneratedUsageMetricsResponseStrictContract {
    Objects.requireNonNull(processInstances, "No processInstances provided.");
    Objects.requireNonNull(decisionInstances, "No decisionInstances provided.");
    Objects.requireNonNull(assignees, "No assignees provided.");
    Objects.requireNonNull(activeTenants, "No activeTenants provided.");
    Objects.requireNonNull(tenants, "No tenants provided.");
  }

  public static ProcessInstancesStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessInstancesStep,
          DecisionInstancesStep,
          AssigneesStep,
          ActiveTenantsStep,
          TenantsStep,
          OptionalStep {
    private Long processInstances;
    private Long decisionInstances;
    private Long assignees;
    private Long activeTenants;
    private java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants;

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
    public ActiveTenantsStep assignees(final Long assignees) {
      this.assignees = assignees;
      return this;
    }

    @Override
    public TenantsStep activeTenants(final Long activeTenants) {
      this.activeTenants = activeTenants;
      return this;
    }

    @Override
    public OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants) {
      this.tenants = tenants;
      return this;
    }

    @Override
    public GeneratedUsageMetricsResponseStrictContract build() {
      return new GeneratedUsageMetricsResponseStrictContract(
          this.processInstances,
          this.decisionInstances,
          this.assignees,
          this.activeTenants,
          this.tenants);
    }
  }

  public interface ProcessInstancesStep {
    DecisionInstancesStep processInstances(final Long processInstances);
  }

  public interface DecisionInstancesStep {
    AssigneesStep decisionInstances(final Long decisionInstances);
  }

  public interface AssigneesStep {
    ActiveTenantsStep assignees(final Long assignees);
  }

  public interface ActiveTenantsStep {
    TenantsStep activeTenants(final Long activeTenants);
  }

  public interface TenantsStep {
    OptionalStep tenants(
        final java.util.Map<String, GeneratedUsageMetricsResponseItemStrictContract> tenants);
  }

  public interface OptionalStep {
    GeneratedUsageMetricsResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_INSTANCES =
        ContractPolicy.field("UsageMetricsResponse", "processInstances");
    public static final ContractPolicy.FieldRef DECISION_INSTANCES =
        ContractPolicy.field("UsageMetricsResponse", "decisionInstances");
    public static final ContractPolicy.FieldRef ASSIGNEES =
        ContractPolicy.field("UsageMetricsResponse", "assignees");
    public static final ContractPolicy.FieldRef ACTIVE_TENANTS =
        ContractPolicy.field("UsageMetricsResponse", "activeTenants");
    public static final ContractPolicy.FieldRef TENANTS =
        ContractPolicy.field("UsageMetricsResponse", "tenants");

    private Fields() {}
  }
}
