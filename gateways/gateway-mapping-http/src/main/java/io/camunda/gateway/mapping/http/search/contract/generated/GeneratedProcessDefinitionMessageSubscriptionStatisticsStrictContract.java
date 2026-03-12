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
public record GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract(
    String processDefinitionId,
    String tenantId,
    String processDefinitionKey,
    Long processInstancesWithActiveSubscriptions,
    Long activeSubscriptions) {

  public GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract {
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstancesWithActiveSubscriptions,
        "processInstancesWithActiveSubscriptions is required and must not be null");
    Objects.requireNonNull(
        activeSubscriptions, "activeSubscriptions is required and must not be null");
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
          TenantIdStep,
          ProcessDefinitionKeyStep,
          ProcessInstancesWithActiveSubscriptionsStep,
          ActiveSubscriptionsStep,
          OptionalStep {
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Long processInstancesWithActiveSubscriptions;
    private ContractPolicy.FieldPolicy<Long> processInstancesWithActiveSubscriptionsPolicy;
    private Long activeSubscriptions;
    private ContractPolicy.FieldPolicy<Long> activeSubscriptionsPolicy;

    private Builder() {}

    @Override
    public TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstancesWithActiveSubscriptionsStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public ActiveSubscriptionsStep processInstancesWithActiveSubscriptions(
        final Long processInstancesWithActiveSubscriptions,
        final ContractPolicy.FieldPolicy<Long> policy) {
      this.processInstancesWithActiveSubscriptions = processInstancesWithActiveSubscriptions;
      this.processInstancesWithActiveSubscriptionsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep activeSubscriptions(
        final Long activeSubscriptions, final ContractPolicy.FieldPolicy<Long> policy) {
      this.activeSubscriptions = activeSubscriptions;
      this.activeSubscriptionsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract(
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          applyRequiredPolicy(
              this.processInstancesWithActiveSubscriptions,
              this.processInstancesWithActiveSubscriptionsPolicy,
              Fields.PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS),
          applyRequiredPolicy(
              this.activeSubscriptions,
              this.activeSubscriptionsPolicy,
              Fields.ACTIVE_SUBSCRIPTIONS));
    }
  }

  public interface ProcessDefinitionIdStep {
    TenantIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    ProcessDefinitionKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstancesWithActiveSubscriptionsStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstancesWithActiveSubscriptionsStep {
    ActiveSubscriptionsStep processInstancesWithActiveSubscriptions(
        final Long processInstancesWithActiveSubscriptions,
        final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface ActiveSubscriptionsStep {
    OptionalStep activeSubscriptions(
        final Long activeSubscriptions, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field(
            "ProcessDefinitionMessageSubscriptionStatisticsResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field(
            "ProcessDefinitionMessageSubscriptionStatisticsResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS =
        ContractPolicy.field(
            "ProcessDefinitionMessageSubscriptionStatisticsResult",
            "processInstancesWithActiveSubscriptions");
    public static final ContractPolicy.FieldRef ACTIVE_SUBSCRIPTIONS =
        ContractPolicy.field(
            "ProcessDefinitionMessageSubscriptionStatisticsResult", "activeSubscriptions");

    private Fields() {}
  }
}
