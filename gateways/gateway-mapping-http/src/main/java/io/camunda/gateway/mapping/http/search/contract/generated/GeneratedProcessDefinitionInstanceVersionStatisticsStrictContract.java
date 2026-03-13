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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract(
    String processDefinitionId,
    String processDefinitionKey,
    @Nullable String processDefinitionName,
    String tenantId,
    Integer processDefinitionVersion,
    Long activeInstancesWithIncidentCount,
    Long activeInstancesWithoutIncidentCount) {

  public GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithIncidentCount,
        "activeInstancesWithIncidentCount is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithoutIncidentCount,
        "activeInstancesWithoutIncidentCount is required and must not be null");
  }

  public static String coerceProcessDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static ProcessDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ProcessDefinitionIdStep,
          ProcessDefinitionKeyStep,
          TenantIdStep,
          ProcessDefinitionVersionStep,
          ActiveInstancesWithIncidentCountStep,
          ActiveInstancesWithoutIncidentCountStep,
          OptionalStep {
    private String processDefinitionId;
    private Object processDefinitionKey;
    private String processDefinitionName;
    private String tenantId;
    private Integer processDefinitionVersion;
    private Long activeInstancesWithIncidentCount;
    private Long activeInstancesWithoutIncidentCount;

    private Builder() {}

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ActiveInstancesWithIncidentCountStep processDefinitionVersion(
        final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public ActiveInstancesWithoutIncidentCountStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount) {
      this.activeInstancesWithIncidentCount = activeInstancesWithIncidentCount;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount) {
      this.activeInstancesWithoutIncidentCount = activeInstancesWithoutIncidentCount;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final @Nullable String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final @Nullable String processDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract(
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.processDefinitionName,
          this.tenantId,
          this.processDefinitionVersion,
          this.activeInstancesWithIncidentCount,
          this.activeInstancesWithoutIncidentCount);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionKeyStep {
    TenantIdStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface TenantIdStep {
    ProcessDefinitionVersionStep tenantId(final String tenantId);
  }

  public interface ProcessDefinitionVersionStep {
    ActiveInstancesWithIncidentCountStep processDefinitionVersion(
        final Integer processDefinitionVersion);
  }

  public interface ActiveInstancesWithIncidentCountStep {
    ActiveInstancesWithoutIncidentCountStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount);
  }

  public interface ActiveInstancesWithoutIncidentCountStep {
    OptionalStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionName(final @Nullable String processDefinitionName);

    OptionalStep processDefinitionName(
        final @Nullable String processDefinitionName,
        final ContractPolicy.FieldPolicy<String> policy);

    GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult", "processDefinitionName");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionInstanceVersionStatisticsResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_INCIDENT_COUNT =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult", "activeInstancesWithIncidentCount");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT =
        ContractPolicy.field(
            "ProcessDefinitionInstanceVersionStatisticsResult",
            "activeInstancesWithoutIncidentCount");

    private Fields() {}
  }
}
