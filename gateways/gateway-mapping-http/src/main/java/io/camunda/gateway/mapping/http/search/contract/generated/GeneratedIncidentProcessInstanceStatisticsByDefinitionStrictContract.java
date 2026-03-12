/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract(
    String processDefinitionId,
    String processDefinitionKey,
    String processDefinitionName,
    Integer processDefinitionVersion,
    String tenantId,
    Long activeInstancesWithErrorCount) {

  public GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionName, "processDefinitionName is required and must not be null");
    Objects.requireNonNull(
        processDefinitionVersion, "processDefinitionVersion is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        activeInstancesWithErrorCount,
        "activeInstancesWithErrorCount is required and must not be null");
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
          ProcessDefinitionKeyStep,
          ProcessDefinitionNameStep,
          ProcessDefinitionVersionStep,
          TenantIdStep,
          ActiveInstancesWithErrorCountStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private String processDefinitionName;
    private ContractPolicy.FieldPolicy<String> processDefinitionNamePolicy;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Long activeInstancesWithErrorCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithErrorCountPolicy;

    private Builder() {}

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionNameStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionName = processDefinitionName;
      this.processDefinitionNamePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithErrorCountStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithErrorCount(
        final Long activeInstancesWithErrorCount, final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithErrorCount = activeInstancesWithErrorCount;
      this.activeInstancesWithErrorCountPolicy = policy;
      return this;
    }

    @Override
    public GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract build() {
      return new GeneratedIncidentProcessInstanceStatisticsByDefinitionStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          applyRequiredPolicy(
              this.processDefinitionName,
              this.processDefinitionNamePolicy,
              Fields.PROCESS_DEFINITION_NAME),
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(
              this.activeInstancesWithErrorCount,
              this.activeInstancesWithErrorCountPolicy,
              Fields.ACTIVE_INSTANCES_WITH_ERROR_COUNT));
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessDefinitionNameStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionNameStep {
    ProcessDefinitionVersionStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    TenantIdStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface TenantIdStep {
    ActiveInstancesWithErrorCountStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ActiveInstancesWithErrorCountStep {
    OptionalStep activeInstancesWithErrorCount(
        final Long activeInstancesWithErrorCount, final ContractPolicy.FieldPolicy<Long> policy);
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
