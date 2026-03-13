/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-definitions.yaml#/components/schemas/ProcessDefinitionInstanceStatisticsResult
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
public record GeneratedProcessDefinitionInstanceStatisticsStrictContract(
    String processDefinitionId,
    String tenantId,
    @Nullable String latestProcessDefinitionName,
    Boolean hasMultipleVersions,
    Long activeInstancesWithoutIncidentCount,
    Long activeInstancesWithIncidentCount
) {

  public GeneratedProcessDefinitionInstanceStatisticsStrictContract {
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(hasMultipleVersions, "hasMultipleVersions is required and must not be null");
    Objects.requireNonNull(activeInstancesWithoutIncidentCount, "activeInstancesWithoutIncidentCount is required and must not be null");
    Objects.requireNonNull(activeInstancesWithIncidentCount, "activeInstancesWithIncidentCount is required and must not be null");
  }


  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ProcessDefinitionIdStep, TenantIdStep, HasMultipleVersionsStep, ActiveInstancesWithoutIncidentCountStep, ActiveInstancesWithIncidentCountStep, OptionalStep {
    private String processDefinitionId;
    private String tenantId;
    private String latestProcessDefinitionName;
    private Boolean hasMultipleVersions;
    private Long activeInstancesWithoutIncidentCount;
    private Long activeInstancesWithIncidentCount;

    private Builder() {}

    @Override
    public TenantIdStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public HasMultipleVersionsStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ActiveInstancesWithoutIncidentCountStep hasMultipleVersions(final Boolean hasMultipleVersions) {
      this.hasMultipleVersions = hasMultipleVersions;
      return this;
    }

    @Override
    public ActiveInstancesWithIncidentCountStep activeInstancesWithoutIncidentCount(final Long activeInstancesWithoutIncidentCount) {
      this.activeInstancesWithoutIncidentCount = activeInstancesWithoutIncidentCount;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithIncidentCount(final Long activeInstancesWithIncidentCount) {
      this.activeInstancesWithIncidentCount = activeInstancesWithIncidentCount;
      return this;
    }

    @Override
    public OptionalStep latestProcessDefinitionName(final @Nullable String latestProcessDefinitionName) {
      this.latestProcessDefinitionName = latestProcessDefinitionName;
      return this;
    }

    @Override
    public OptionalStep latestProcessDefinitionName(final @Nullable String latestProcessDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.latestProcessDefinitionName = policy.apply(latestProcessDefinitionName, Fields.LATEST_PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionInstanceStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionInstanceStatisticsStrictContract(
          this.processDefinitionId,
          this.tenantId,
          this.latestProcessDefinitionName,
          this.hasMultipleVersions,
          this.activeInstancesWithoutIncidentCount,
          this.activeInstancesWithIncidentCount);
    }
  }

  public interface ProcessDefinitionIdStep {
    TenantIdStep processDefinitionId(final String processDefinitionId);
  }

  public interface TenantIdStep {
    HasMultipleVersionsStep tenantId(final String tenantId);
  }

  public interface HasMultipleVersionsStep {
    ActiveInstancesWithoutIncidentCountStep hasMultipleVersions(final Boolean hasMultipleVersions);
  }

  public interface ActiveInstancesWithoutIncidentCountStep {
    ActiveInstancesWithIncidentCountStep activeInstancesWithoutIncidentCount(final Long activeInstancesWithoutIncidentCount);
  }

  public interface ActiveInstancesWithIncidentCountStep {
    OptionalStep activeInstancesWithIncidentCount(final Long activeInstancesWithIncidentCount);
  }

  public interface OptionalStep {
  OptionalStep latestProcessDefinitionName(final @Nullable String latestProcessDefinitionName);

  OptionalStep latestProcessDefinitionName(final @Nullable String latestProcessDefinitionName, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedProcessDefinitionInstanceStatisticsStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "tenantId");
    public static final ContractPolicy.FieldRef LATEST_PROCESS_DEFINITION_NAME = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "latestProcessDefinitionName");
    public static final ContractPolicy.FieldRef HAS_MULTIPLE_VERSIONS = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "hasMultipleVersions");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "activeInstancesWithoutIncidentCount");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_INCIDENT_COUNT = ContractPolicy.field("ProcessDefinitionInstanceStatisticsResult", "activeInstancesWithIncidentCount");

    private Fields() {}
  }


}
