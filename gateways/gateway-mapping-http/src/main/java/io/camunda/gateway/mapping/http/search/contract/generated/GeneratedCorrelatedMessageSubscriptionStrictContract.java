/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/CorrelatedMessageSubscriptionResult
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
    String tenantId
) {

  public GeneratedCorrelatedMessageSubscriptionStrictContract {
    Objects.requireNonNull(correlationTime, "correlationTime is required and must not be null");
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(messageKey, "messageKey is required and must not be null");
    Objects.requireNonNull(messageName, "messageName is required and must not be null");
    Objects.requireNonNull(partitionId, "partitionId is required and must not be null");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId is required and must not be null");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey is required and must not be null");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey is required and must not be null");
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



  public static CorrelationTimeStep builder() {
    return new Builder();
  }

  public static final class Builder implements CorrelationTimeStep, ElementIdStep, MessageKeyStep, MessageNameStep, PartitionIdStep, ProcessDefinitionIdStep, ProcessDefinitionKeyStep, ProcessInstanceKeyStep, SubscriptionKeyStep, TenantIdStep, OptionalStep {
    private String correlationKey;
    private String correlationTime;
    private String elementId;
    private Object elementInstanceKey;
    private Object messageKey;
    private String messageName;
    private Integer partitionId;
    private String processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private Object subscriptionKey;
    private String tenantId;

    private Builder() {}

    @Override
    public ElementIdStep correlationTime(final String correlationTime) {
      this.correlationTime = correlationTime;
      return this;
    }

    @Override
    public MessageKeyStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public MessageNameStep messageKey(final Object messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    @Override
    public PartitionIdStep messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    @Override
    public ProcessDefinitionIdStep partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public SubscriptionKeyStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public TenantIdStep subscriptionKey(final Object subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
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

    public Builder elementInstanceKey(final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
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

    public Builder rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedCorrelatedMessageSubscriptionStrictContract build() {
      return new GeneratedCorrelatedMessageSubscriptionStrictContract(
          this.correlationKey,
          this.correlationTime,
          this.elementId,
          coerceElementInstanceKey(this.elementInstanceKey),
          coerceMessageKey(this.messageKey),
          this.messageName,
          this.partitionId,
          this.processDefinitionId,
          coerceProcessDefinitionKey(this.processDefinitionKey),
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          coerceSubscriptionKey(this.subscriptionKey),
          this.tenantId);
    }
  }

  public interface CorrelationTimeStep {
    ElementIdStep correlationTime(final String correlationTime);
  }

  public interface ElementIdStep {
    MessageKeyStep elementId(final String elementId);
  }

  public interface MessageKeyStep {
    MessageNameStep messageKey(final Object messageKey);
  }

  public interface MessageNameStep {
    PartitionIdStep messageName(final String messageName);
  }

  public interface PartitionIdStep {
    ProcessDefinitionIdStep partitionId(final Integer partitionId);
  }

  public interface ProcessDefinitionIdStep {
    ProcessDefinitionKeyStep processDefinitionId(final String processDefinitionId);
  }

  public interface ProcessDefinitionKeyStep {
    ProcessInstanceKeyStep processDefinitionKey(final Object processDefinitionKey);
  }

  public interface ProcessInstanceKeyStep {
    SubscriptionKeyStep processInstanceKey(final Object processInstanceKey);
  }

  public interface SubscriptionKeyStep {
    TenantIdStep subscriptionKey(final Object subscriptionKey);
  }

  public interface TenantIdStep {
    OptionalStep tenantId(final String tenantId);
  }

  public interface OptionalStep {
  OptionalStep correlationKey(final @Nullable String correlationKey);

  OptionalStep correlationKey(final @Nullable String correlationKey, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey);

  OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

  OptionalStep elementInstanceKey(final @Nullable String elementInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedCorrelatedMessageSubscriptionStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef CORRELATION_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "correlationKey");
    public static final ContractPolicy.FieldRef CORRELATION_TIME = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "correlationTime");
    public static final ContractPolicy.FieldRef ELEMENT_ID = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "elementInstanceKey");
    public static final ContractPolicy.FieldRef MESSAGE_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "messageKey");
    public static final ContractPolicy.FieldRef MESSAGE_NAME = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "messageName");
    public static final ContractPolicy.FieldRef PARTITION_ID = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "partitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef SUBSCRIPTION_KEY = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "subscriptionKey");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("CorrelatedMessageSubscriptionResult", "tenantId");

    private Fields() {}
  }


}
