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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract(
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("processDefinitionKey") String processDefinitionKey,
    @JsonProperty("processDefinitionName") String processDefinitionName,
    @JsonProperty("processDefinitionVersion") Integer processDefinitionVersion,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("activeInstancesWithErrorCount") Long activeInstancesWithErrorCount) {

  public GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract {
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(processDefinitionKey, "No processDefinitionKey provided.");
    Objects.requireNonNull(processDefinitionName, "No processDefinitionName provided.");
    Objects.requireNonNull(processDefinitionVersion, "No processDefinitionVersion provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(
        activeInstancesWithErrorCount, "No activeInstancesWithErrorCount provided.");
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
          ProcessDefinitionNameStep,
          ProcessDefinitionVersionStep,
          TenantIdStep,
          ActiveInstancesWithErrorCountStep,
          OptionalStep {
    private String processDefinitionId;
    private Object processDefinitionKey;
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private String tenantId;
    private Long activeInstancesWithErrorCount;

    private Builder() {}

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ProcessDefinitionNameStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public ActiveInstancesWithErrorCountStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithErrorCount(final Long activeInstancesWithErrorCount) {
      this.activeInstancesWithErrorCount = activeInstancesWithErrorCount;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract(
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.processDefinitionName,
          this.processDefinitionVersion,
          this.tenantId,
          this.activeInstancesWithErrorCount);
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessDefinitionNameStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessDefinitionNameStep {
    ProcessDefinitionVersionStep processDefinitionName(final String processDefinitionName);
  }

  public interface ProcessDefinitionVersionStep {
    TenantIdStep processDefinitionVersion(final Integer processDefinitionVersion);
  }

  public interface TenantIdStep {
    ActiveInstancesWithErrorCountStep tenantId(final String tenantId);
  }

  public interface ActiveInstancesWithErrorCountStep {
    OptionalStep activeInstancesWithErrorCount(final Long activeInstancesWithErrorCount);
  }

  public interface OptionalStep {
    GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionResult", "processDefinitionName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionResult", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("IncidentProcessInstanceStatisticsByDefinitionResult", "tenantId");
    public static final ContractPolicy.FieldRef ACTIVE_INSTANCES_WITH_ERROR_COUNT =
        ContractPolicy.field(
            "IncidentProcessInstanceStatisticsByDefinitionResult", "activeInstancesWithErrorCount");

    private Fields() {}
  }
}
