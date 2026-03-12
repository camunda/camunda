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
public record GeneratedCorrelatedMessageSubscriptionStrictContract(
    @Nullable String correlationKey,
    String correlationTime,
    String elementId,
    @Nullable String elementInstanceKey,
    String messageKey,
    String messageName,
    Integer partitionId,
    String processDefinitionId,
    String processDefinitionKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String subscriptionKey,
    String tenantId) {

  public GeneratedCorrelatedMessageSubscriptionStrictContract {
    Objects.requireNonNull(correlationTime, "correlationTime is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(messageKey, "messageKey is required and must not be null");
    Objects.requireNonNull(messageName, "messageName is required and must not be null");
    Objects.requireNonNull(partitionId, "partitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(
        processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(subscriptionKey, "subscriptionKey is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
  }

  public static String coerceElementInstanceKey(final Object value) {
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
        "elementInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceMessageKey(final Object value) {
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
        "messageKey must be a String or Number, but was " + value.getClass().getName());
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

  public static String coerceProcessInstanceKey(final Object value) {
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
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceRootProcessInstanceKey(final Object value) {
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
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static String coerceSubscriptionKey(final Object value) {
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
        "subscriptionKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static CorrelationTimeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CorrelationTimeStep,
          ElementIdStep,
          MessageKeyStep,
          MessageNameStep,
          PartitionIdStep,
          ProcessDefinitionIdStep,
          ProcessDefinitionKeyStep,
          ProcessInstanceKeyStep,
          SubscriptionKeyStep,
          TenantIdStep,
          OptionalStep {
    private String correlationKey;
    private String correlationTime;
    private ContractPolicy.FieldPolicy<String> correlationTimePolicy;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private Object elementInstanceKey;
    private Object messageKey;
    private ContractPolicy.FieldPolicy<Object> messageKeyPolicy;
    private String messageName;
    private ContractPolicy.FieldPolicy<String> messageNamePolicy;
    private Integer partitionId;
    private ContractPolicy.FieldPolicy<Integer> partitionIdPolicy;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Object processDefinitionKey;
    private ContractPolicy.FieldPolicy<Object> processDefinitionKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private Object subscriptionKey;
    private ContractPolicy.FieldPolicy<Object> subscriptionKeyPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;

    private Builder() {}

    @Override
    public ElementIdStep correlationTime(
        final String correlationTime, final ContractPolicy.FieldPolicy<String> policy) {
      this.correlationTime = correlationTime;
      this.correlationTimePolicy = policy;
      return this;
    }

    @Override
    public MessageKeyStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public MessageNameStep messageKey(
        final Object messageKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageKey = messageKey;
      this.messageKeyPolicy = policy;
      return this;
    }

    @Override
    public PartitionIdStep messageName(
        final String messageName, final ContractPolicy.FieldPolicy<String> policy) {
      this.messageName = messageName;
      this.messageNamePolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.partitionId = partitionId;
      this.partitionIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey = processDefinitionKey;
      this.processDefinitionKeyPolicy = policy;
      return this;
    }

    @Override
    public SubscriptionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep subscriptionKey(
        final Object subscriptionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.subscriptionKey = subscriptionKey;
      this.subscriptionKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(
        final String correlationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedCorrelatedMessageSubscriptionStrictContract build() {
      return new GeneratedCorrelatedMessageSubscriptionStrictContract(
          this.correlationKey,
          applyRequiredPolicy(
              this.correlationTime, this.correlationTimePolicy, Fields.CORRELATION_TIME),
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceMessageKey(
              applyRequiredPolicy(this.messageKey, this.messageKeyPolicy, Fields.MESSAGE_KEY)),
          applyRequiredPolicy(this.messageName, this.messageNamePolicy, Fields.MESSAGE_NAME),
          applyRequiredPolicy(this.partitionId, this.partitionIdPolicy, Fields.PARTITION_ID),
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          coerceProcessDefinitionKey(
              applyRequiredPolicy(
                  this.processDefinitionKey,
                  this.processDefinitionKeyPolicy,
                  Fields.PROCESS_DEFINITION_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceSubscriptionKey(
              applyRequiredPolicy(
                  this.subscriptionKey, this.subscriptionKeyPolicy, Fields.SUBSCRIPTION_KEY)),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID));
    }
  }

  public interface CorrelationTimeStep {
    ElementIdStep correlationTime(
        final String correlationTime, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementIdStep {
    MessageKeyStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MessageKeyStep {
    MessageNameStep messageKey(
        final Object messageKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface MessageNameStep {
    PartitionIdStep messageName(
        final String messageName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface PartitionIdStep {
    ProcessDefinitionIdStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    SubscriptionKeyStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface SubscriptionKeyStep {
    TenantIdStep subscriptionKey(
        final Object subscriptionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep correlationKey(final String correlationKey);

    OptionalStep correlationKey(
        final String correlationKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedCorrelatedMessageSubscriptionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CORRELATION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "correlationKey");
    public static final ContractPolicy.FieldRef CORRELATION_TIME =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "correlationTime");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef MESSAGE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "messageKey");
    public static final ContractPolicy.FieldRef MESSAGE_NAME =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "messageName");
    public static final ContractPolicy.FieldRef PARTITION_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "partitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef SUBSCRIPTION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "subscriptionKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionResult", "tenantId");

    private Fields() {}
  }
}
