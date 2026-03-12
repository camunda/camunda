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
import org.springframework.lang.Nullable;

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
          TenantIdStep,
          ProcessDefinitionVersionStep,
          ActiveInstancesWithIncidentCountStep,
          ActiveInstancesWithoutIncidentCountStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private String processDefinitionName;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Integer processDefinitionVersion;
    private ContractPolicy.FieldPolicy<Integer> processDefinitionVersionPolicy;
    private Long activeInstancesWithIncidentCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithIncidentCountPolicy;
    private Long activeInstancesWithoutIncidentCount;
    private ContractPolicy.FieldPolicy<Long> activeInstancesWithoutIncidentCountPolicy;

    private Builder() {}

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionVersionStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithIncidentCountStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.processDefinitionVersion = processDefinitionVersion;
      this.processDefinitionVersionPolicy = policy;
      return this;
    }

    @Override
    public ActiveInstancesWithoutIncidentCountStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithIncidentCount = activeInstancesWithIncidentCount;
      this.activeInstancesWithIncidentCountPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeInstancesWithoutIncidentCount = activeInstancesWithoutIncidentCount;
      this.activeInstancesWithoutIncidentCountPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionInstanceVersionStatisticsStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          this.processDefinitionName,
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(
              this.processDefinitionVersion,
              this.processDefinitionVersionPolicy,
              Fields.PROCESS_DEFINITION_VERSION),
          applyRequiredPolicy(
              this.activeInstancesWithIncidentCount,
              this.activeInstancesWithIncidentCountPolicy,
              Fields.ACTIVE_INSTANCES_WITH_INCIDENT_COUNT),
          applyRequiredPolicy(
              this.activeInstancesWithoutIncidentCount,
              this.activeInstancesWithoutIncidentCountPolicy,
              Fields.ACTIVE_INSTANCES_WITHOUT_INCIDENT_COUNT));
    }
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    TenantIdStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TenantIdStep {
    ProcessDefinitionVersionStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionVersionStep {
    ActiveInstancesWithIncidentCountStep processDefinitionVersion(
        final Integer processDefinitionVersion, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ActiveInstancesWithIncidentCountStep {
    ActiveInstancesWithoutIncidentCountStep activeInstancesWithIncidentCount(
        final Long activeInstancesWithIncidentCount, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface ActiveInstancesWithoutIncidentCountStep {
    OptionalStep activeInstancesWithoutIncidentCount(
        final Long activeInstancesWithoutIncidentCount,
        final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionName(final String processDefinitionName);

    OptionalStep processDefinitionName(
        final String processDefinitionName, final ContractPolicy.FieldPolicy<String> policy);

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
