/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/MessageSubscriptionResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMessageSubscriptionStrictContract(
    @JsonProperty("messageSubscriptionKey") String messageSubscriptionKey,
    @JsonProperty("processDefinitionId") String processDefinitionId,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("processInstanceKey") @Nullable String processInstanceKey,
    @JsonProperty("rootProcessInstanceKey") @Nullable String rootProcessInstanceKey,
    @JsonProperty("elementId") String elementId,
    @JsonProperty("elementInstanceKey") @Nullable String elementInstanceKey,
    @JsonProperty("messageSubscriptionState")
        io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedMessageSubscriptionStateEnum
            messageSubscriptionState,
    @JsonProperty("lastUpdatedDate") String lastUpdatedDate,
    @JsonProperty("messageName") String messageName,
    @JsonProperty("correlationKey") @Nullable String correlationKey,
    @JsonProperty("tenantId") String tenantId) {

  public GeneratedMessageSubscriptionStrictContract {
    Objects.requireNonNull(messageSubscriptionKey, "No messageSubscriptionKey provided.");
    Objects.requireNonNull(processDefinitionId, "No processDefinitionId provided.");
    Objects.requireNonNull(elementId, "No elementId provided.");
    Objects.requireNonNull(messageSubscriptionState, "No messageSubscriptionState provided.");
    Objects.requireNonNull(lastUpdatedDate, "No lastUpdatedDate provided.");
    Objects.requireNonNull(messageName, "No messageName provided.");
    Objects.requireNonNull(tenantId, "No tenantId provided.");
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
    private String processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private String elementId;
    private Object elementInstanceKey;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedMessageSubscriptionStateEnum
        messageSubscriptionState;
    private String lastUpdatedDate;
    private String messageName;
    private String correlationKey;
    private String tenantId;

    private Builder() {}

    @Override
    public ProcessDefinitionIdStep messageSubscriptionKey(final Object messageSubscriptionKey) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      return this;
    }

    @Override
    public ElementIdStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public MessageSubscriptionStateStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public LastUpdatedDateStep messageSubscriptionState(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedMessageSubscriptionStateEnum
            messageSubscriptionState) {
      this.messageSubscriptionState = messageSubscriptionState;
      return this;
    }

    @Override
    public MessageNameStep lastUpdatedDate(final String lastUpdatedDate) {
      this.lastUpdatedDate = lastUpdatedDate;
      return this;
    }

    @Override
    public TenantIdStep messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final @Nullable String processInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(
        final @Nullable String elementInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(
        final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
      return this;
    }

    @Override
    public GeneratedMessageSubscriptionStrictContract build() {
      return new GeneratedMessageSubscriptionStrictContract(
          coerceMessageSubscriptionKey(this.messageSubscriptionKey),
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          this.elementId,
          coerceElementInstanceKey(this.elementInstanceKey),
          this.messageSubscriptionState,
          this.lastUpdatedDate,
          this.messageName,
          this.correlationKey,
          this.tenantId);
    }
  }

  public interface MessageSubscriptionKeyStep {
    ProcessDefinitionIdStep messageSubscriptionKey(final Object messageSubscriptionKey);
  }

  public interface ProcessDefinitionIdStep {
    ElementIdStep processDefinitionId(final String processDefinitionId);
  }

  public interface ElementIdStep {
    MessageSubscriptionStateStep elementId(final String elementId);
  }

  public interface MessageSubscriptionStateStep {
    LastUpdatedDateStep messageSubscriptionState(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedMessageSubscriptionStateEnum
            messageSubscriptionState);
  }

  public interface LastUpdatedDateStep {
    MessageNameStep lastUpdatedDate(final String lastUpdatedDate);
  }

  public interface MessageNameStep {
    TenantIdStep messageName(final String messageName);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId);
  }

  public interface OptionalStep {
    OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final @Nullable String rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final @Nullable Object rootProcessInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep correlationKey(final @Nullable String correlationKey);

    OptionalStep correlationKey(
        final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy);

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
