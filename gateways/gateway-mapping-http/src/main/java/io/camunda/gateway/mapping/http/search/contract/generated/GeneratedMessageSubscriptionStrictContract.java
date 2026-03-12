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
public record GeneratedMessageSubscriptionStrictContract(
    String messageSubscriptionKey,
    String processDefinitionId,
    @Nullable String processDefinitionKey,
    @Nullable String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String elementId,
    @Nullable String elementInstanceKey,
    io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum messageSubscriptionState,
    String lastUpdatedDate,
    String messageName,
    @Nullable String correlationKey,
    String tenantId) {

  public GeneratedMessageSubscriptionStrictContract {
    Objects.requireNonNull(
        messageSubscriptionKey, "messageSubscriptionKey is required and must not be null");
    Objects.requireNonNull(
        processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(
        messageSubscriptionState, "messageSubscriptionState is required and must not be null");
    Objects.requireNonNull(lastUpdatedDate, "lastUpdatedDate is required and must not be null");
    Objects.requireNonNull(messageName, "messageName is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
  }

  public static String coerceMessageSubscriptionKey(final Object value) {
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
        "messageSubscriptionKey must be a String or Number, but was " + value.getClass().getName());
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static MessageSubscriptionKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements MessageSubscriptionKeyStep,
          ProcessDefinitionIdStep,
          ElementIdStep,
          MessageSubscriptionStateStep,
          LastUpdatedDateStep,
          MessageNameStep,
          TenantIdStep,
          OptionalStep {
    private Object messageSubscriptionKey;
    private ContractPolicy.FieldPolicy<Object> messageSubscriptionKeyPolicy;
    private String processDefinitionId;
    private ContractPolicy.FieldPolicy<String> processDefinitionIdPolicy;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private Object elementInstanceKey;
    private io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum messageSubscriptionState;
    private ContractPolicy.FieldPolicy<
            io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum>
        messageSubscriptionStatePolicy;
    private String lastUpdatedDate;
    private ContractPolicy.FieldPolicy<String> lastUpdatedDatePolicy;
    private String messageName;
    private ContractPolicy.FieldPolicy<String> messageNamePolicy;
    private String correlationKey;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;

    private Builder() {}

    @Override
    public ProcessDefinitionIdStep messageSubscriptionKey(
        final Object messageSubscriptionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      this.messageSubscriptionKeyPolicy = policy;
      return this;
    }

    @Override
    public ElementIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = processDefinitionId;
      this.processDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public MessageSubscriptionStateStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public LastUpdatedDateStep messageSubscriptionState(
        final io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum
            messageSubscriptionState,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum>
            policy) {
      this.messageSubscriptionState = messageSubscriptionState;
      this.messageSubscriptionStatePolicy = policy;
      return this;
    }

    @Override
    public MessageNameStep lastUpdatedDate(
        final String lastUpdatedDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastUpdatedDate = lastUpdatedDate;
      this.lastUpdatedDatePolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep messageName(
        final String messageName, final ContractPolicy.FieldPolicy<String> policy) {
      this.messageName = messageName;
      this.messageNamePolicy = policy;
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
    public OptionalStep processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
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
    public GeneratedMessageSubscriptionStrictContract build() {
      return new GeneratedMessageSubscriptionStrictContract(
          coerceMessageSubscriptionKey(
              applyRequiredPolicy(
                  this.messageSubscriptionKey,
                  this.messageSubscriptionKeyPolicy,
                  Fields.MESSAGE_SUBSCRIPTION_KEY)),
          applyRequiredPolicy(
              this.processDefinitionId,
              this.processDefinitionIdPolicy,
              Fields.PROCESS_DEFINITION_ID),
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          coerceElementInstanceKey(this.elementInstanceKey),
          applyRequiredPolicy(
              this.messageSubscriptionState,
              this.messageSubscriptionStatePolicy,
              Fields.MESSAGE_SUBSCRIPTION_STATE),
          applyRequiredPolicy(
              this.lastUpdatedDate, this.lastUpdatedDatePolicy, Fields.LAST_UPDATED_DATE),
          applyRequiredPolicy(this.messageName, this.messageNamePolicy, Fields.MESSAGE_NAME),
          this.correlationKey,
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID));
    }
  }

  public interface MessageSubscriptionKeyStep {
    ProcessDefinitionIdStep messageSubscriptionKey(
        final Object messageSubscriptionKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessDefinitionIdStep {
    ElementIdStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ElementIdStep {
    MessageSubscriptionStateStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MessageSubscriptionStateStep {
    LastUpdatedDateStep messageSubscriptionState(
        final io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum
            messageSubscriptionState,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum>
            policy);
  }

  public interface LastUpdatedDateStep {
    MessageNameStep lastUpdatedDate(
        final String lastUpdatedDate, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MessageNameStep {
    TenantIdStep messageName(
        final String messageName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionKey(final String processDefinitionKey);

    OptionalStep processDefinitionKey(final Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final String processDefinitionKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final Object processDefinitionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final String elementInstanceKey);

    OptionalStep elementInstanceKey(final Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep correlationKey(final String correlationKey);

    OptionalStep correlationKey(
        final String correlationKey, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedMessageSubscriptionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef MESSAGE_SUBSCRIPTION_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "messageSubscriptionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("MessageSubscriptionResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("MessageSubscriptionResult", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef MESSAGE_SUBSCRIPTION_STATE =
        ContractPolicy.field("MessageSubscriptionResult", "messageSubscriptionState");
    public static final ContractPolicy.FieldRef LAST_UPDATED_DATE =
        ContractPolicy.field("MessageSubscriptionResult", "lastUpdatedDate");
    public static final ContractPolicy.FieldRef MESSAGE_NAME =
        ContractPolicy.field("MessageSubscriptionResult", "messageName");
    public static final ContractPolicy.FieldRef CORRELATION_KEY =
        ContractPolicy.field("MessageSubscriptionResult", "correlationKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("MessageSubscriptionResult", "tenantId");

    private Fields() {}
  }
}
