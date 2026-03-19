/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/CorrelatedMessageSubscriptionFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedCorrelatedMessageSubscriptionFilterStrictContract(
    @JsonProperty("correlationKey")
        @Nullable GeneratedStringFilterPropertyStrictContract correlationKey,
    @JsonProperty("correlationTime")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract correlationTime,
    @JsonProperty("elementId") @Nullable GeneratedStringFilterPropertyStrictContract elementId,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("messageKey")
        @Nullable GeneratedBasicStringFilterPropertyStrictContract messageKey,
    @JsonProperty("messageName") @Nullable GeneratedStringFilterPropertyStrictContract messageName,
    @JsonProperty("partitionId") @Nullable GeneratedIntegerFilterPropertyStrictContract partitionId,
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("subscriptionKey")
        @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract subscriptionKey,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract correlationKey;
    private GeneratedDateTimeFilterPropertyStrictContract correlationTime;
    private GeneratedStringFilterPropertyStrictContract elementId;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedBasicStringFilterPropertyStrictContract messageKey;
    private GeneratedStringFilterPropertyStrictContract messageName;
    private GeneratedIntegerFilterPropertyStrictContract partitionId;
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedMessageSubscriptionKeyFilterPropertyStrictContract subscriptionKey;
    private GeneratedStringFilterPropertyStrictContract tenantId;

    private Builder() {}

    @Override
    public OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep correlationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract correlationTime) {
      this.correlationTime = correlationTime;
      return this;
    }

    @Override
    public OptionalStep correlationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract correlationTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.correlationTime = policy.apply(correlationTime, Fields.CORRELATION_TIME, null);
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract
            elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.elementInstanceKey = policy.apply(elementInstanceKey, Fields.ELEMENT_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep messageKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    @Override
    public OptionalStep messageKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract messageKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy) {
      this.messageKey = policy.apply(messageKey, Fields.MESSAGE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName) {
      this.messageName = messageName;
      return this;
    }

    @Override
    public OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.messageName = policy.apply(messageName, Fields.MESSAGE_NAME, null);
      return this;
    }

    @Override
    public OptionalStep partitionId(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public OptionalStep partitionId(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract partitionId,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy) {
      this.partitionId = policy.apply(partitionId, Fields.PARTITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep subscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    @Override
    public OptionalStep subscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract subscriptionKey,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionKeyFilterPropertyStrictContract>
            policy) {
      this.subscriptionKey = policy.apply(subscriptionKey, Fields.SUBSCRIPTION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedCorrelatedMessageSubscriptionFilterStrictContract build() {
      return new GeneratedCorrelatedMessageSubscriptionFilterStrictContract(
          this.correlationKey,
          this.correlationTime,
          this.elementId,
          this.elementInstanceKey,
          this.messageKey,
          this.messageName,
          this.partitionId,
          this.processDefinitionId,
          this.processDefinitionKey,
          this.processInstanceKey,
          this.subscriptionKey,
          this.tenantId);
    }
  }

  public interface OptionalStep {
    OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey);

    OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep correlationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract correlationTime);

    OptionalStep correlationTime(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract correlationTime,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep elementId(final @Nullable GeneratedStringFilterPropertyStrictContract elementId);

    OptionalStep elementId(
        final @Nullable GeneratedStringFilterPropertyStrictContract elementId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedElementInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep messageKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract messageKey);

    OptionalStep messageKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract messageKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName);

    OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep partitionId(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract partitionId);

    OptionalStep partitionId(
        final @Nullable GeneratedIntegerFilterPropertyStrictContract partitionId,
        final ContractPolicy.FieldPolicy<GeneratedIntegerFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep subscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            subscriptionKey);

    OptionalStep subscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract subscriptionKey,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    GeneratedCorrelatedMessageSubscriptionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CORRELATION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "correlationKey");
    public static final ContractPolicy.FieldRef CORRELATION_TIME =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "correlationTime");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef MESSAGE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "messageKey");
    public static final ContractPolicy.FieldRef MESSAGE_NAME =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "messageName");
    public static final ContractPolicy.FieldRef PARTITION_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "partitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef SUBSCRIPTION_KEY =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "subscriptionKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("CorrelatedMessageSubscriptionFilter", "tenantId");

    private Fields() {}
  }
}
