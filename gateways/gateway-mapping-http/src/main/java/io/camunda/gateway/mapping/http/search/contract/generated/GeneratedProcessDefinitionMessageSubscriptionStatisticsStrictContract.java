/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-definitions.yaml#/components/schemas/ProcessDefinitionMessageSubscriptionStatisticsResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract(
    String processDefinitionId,
    String tenantId,
    String processDefinitionKey,
    Long processInstancesWithActiveSubscriptions,
    Long activeSubscriptions
) {

  public GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract {
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(processInstancesWithActiveSubscriptions, "processInstancesWithActiveSubscriptions is required and must not be null");
    Objects.requireNonNull(activeSubscriptions, "activeSubscriptions is required and must not be null");
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

  public static final class Builder implements ProcessDefinitionIdStep, TenantIdStep, ProcessDefinitionKeyStep, ProcessInstancesWithActiveSubscriptionsStep, ActiveSubscriptionsStep, OptionalStep {
    private String processDefinitionId;
    private String tenantId;
    private Object processDefinitionKey;
    private Long processInstancesWithActiveSubscriptions;
    private Long activeSubscriptions;

    private Builder() {}

    @Override
    public TenantIdStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessInstancesWithActiveSubscriptionsStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public ActiveSubscriptionsStep processInstancesWithActiveSubscriptions(final Long processInstancesWithActiveSubscriptions) {
      this.processInstancesWithActiveSubscriptions = processInstancesWithActiveSubscriptions;
      return this;
    }

    @Override
    public OptionalStep activeSubscriptions(final Long activeSubscriptions) {
      this.activeSubscriptions = activeSubscriptions;
      return this;
    }
    @Override
    public GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract build() {
      return new GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract(
          this.processDefinitionId,
          this.tenantId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          this.processInstancesWithActiveSubscriptions,
          this.activeSubscriptions);
    }
  }

  public interface ProcessDefinitionIdStep {
    TenantIdStep processDefinitionId(final String processDefinitionId);
  }

  public interface TenantIdStep {
    ProcessDefinitionKeyStep tenantId(final String tenantId);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstancesWithActiveSubscriptionsStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstancesWithActiveSubscriptionsStep {
    ActiveSubscriptionsStep processInstancesWithActiveSubscriptions(final Long processInstancesWithActiveSubscriptions);
  }

  public interface ActiveSubscriptionsStep {
    OptionalStep activeSubscriptions(final Long activeSubscriptions);
  }

  public interface OptionalStep {
    GeneratedProcessDefinitionMessageSubscriptionStatisticsStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS = ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "processInstancesWithActiveSubscriptions");
    public static final ContractPolicy.FieldRef ACTIVE_SUBSCRIPTIONS = ContractPolicy.field("ProcessDefinitionMessageSubscriptionStatisticsResult", "activeSubscriptions");

    private Fields() {}
  }


}
