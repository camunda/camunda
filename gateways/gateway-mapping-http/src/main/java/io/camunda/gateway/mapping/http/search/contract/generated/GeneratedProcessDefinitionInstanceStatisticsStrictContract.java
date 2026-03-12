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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionInstanceStatisticsStrictContract(
    String processDefinitionId,
    String tenantId,
    @Nullable String latestProcessDefinitionName,
    Boolean hasMultipleVersions,
    Long activeInstancesWithoutIncidentCount,
    Long activeInstancesWithIncidentCount) {

  public GeneratedProcessDefinitionInstanceStatisticsStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        hasMultipleVersions, "hasMultipleVersions is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithoutIncidentCount,
        "activeInstancesWithoutIncidentCount is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithIncidentCount,
        "activeInstancesWithIncidentCount is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessDefinitionIdStep,
          TenantIdStep,
          HasMultipleVersionsStep,
          ActiveInstancesWithoutIncidentCountStep,
          ActiveInstancesWithIncidentCountStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String latestProcessDefinitionName;
    private Boolean hasMultipleVersions;
    private ContractPolicy.FieldPolicy<Boolean> hasMultipleVersionsPolicy;
    private Long activeInstancesWithoutIncidentCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithoutIncidentCountPolicy;
    private Long activeInstancesWithIncidentCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithIncidentCountPolicy;

    private Builder() {}

    @Override
    public TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public HasMultipleVersionsStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithoutIncidentCountStep hasMultipleVersions(
        final Boolean hasMultipleVersions, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.hasMultipleVersions = hasMultipleVersions;
      this.hasMultipleVersionsPolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithIncidentCountStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithoutIncidentCount = activeInstancesWithoutIncidentCount;
      this.activeInstancesWithoutIncidentCountPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithIncidentCount = activeInstancesWithIncidentCount;
      this.activeInstancesWithIncidentCountPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep latestProcessDefinitionName(final String latestProcessDefinitionName) {
      this.latestProcessDefinitionName = latestProcessDefinitionName;
      return this;
    }

    @Override
    public OptionalStep latestProcessDefinitionName(
        final String latestProcessDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.latestProcessDefinitionName =
          policy.apply(latestProcessDefinitionName, Fields.LATEST_PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionInstanceStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionInstanceStatisticsStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          this.latestProcessDefinitionName,
          applyRequiredPolicy(
              this.hasMultipleVersions,
              this.hasMultipleVersionsPolicy,
              Fields.HAS_MULTIPLE_VERSIONS),
          applyRequiredPolicy(
              this.activeInstancesWithoutIncidentCount,
              this.activeInstancesWithoutIncidentCountPolicy,
              Fields.ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT),
          applyRequiredPolicy(
              this.activeInstancesWithIncidentCount,
              this.activeInstancesWithIncidentCountPolicy,
              Fields.ACTIVE_INSTANCES_WITH_INCIDENT_COUNT));
    }
  }

  public interface ProcessDefinitionIdStep {
    TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    HasMultipleVersionsStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface HasMultipleVersionsStep {
    ActiveInstancesWithoutIncidentCountStep hasMultipleVersions(
        final Boolean hasMultipleVersions, final ContractPolicy.FieldPolicy<Boolean> policy);
  }

  public interface ActiveInstancesWithoutIncidentCountStep {
    ActiveInstancesWithIncidentCountStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface ActiveInstancesWithIncidentCountStep {
    OptionalStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    OptionalStep latestProcessDefinitionName(final String latestProcessDefinitionName);

    OptionalStep latestProcessDefinitionName(
        final String latestProcessDefinitionName, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessDefinitionInstanceStatisticsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "tenantId");
    public static final ContractPolicy.FieldRef LATEST_PROCESS_DEFINITION_NAME =
        ContractPolicy.field(
            "ProcessDefinitionInstanceStatisticsResult", "latestProcessDefinitionName");
    public static final ContractPolicy.FieldRef HAS_MULTIPLE_VERSIONS =
        ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "hasMultipleVersions");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT =
        ContractPolicy.field(
            "ProcessDefinitionInstanceStatisticsResult", "activeInstancesWithoutIncidentCount");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_INCIDENT_COUNT =
        ContractPolicy.field(
            "ProcessDefinitionInstanceStatisticsResult", "activeInstancesWithIncidentCount");

    private Fields() {}
  }
}
