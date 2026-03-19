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
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMessageSubscriptionFilterStrictContract(
    @JsonProperty("messageSubscriptionKey")
        @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            messageSubscriptionKey,
    @JsonProperty("processDefinitionKey")
        @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey,
    @JsonProperty("processDefinitionId")
        @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("elementId") @Nullable GeneratedStringFilterPropertyStrictContract elementId,
    @JsonProperty("elementInstanceKey")
        @Nullable GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey,
    @JsonProperty("messageSubscriptionState")
        @Nullable GeneratedMessageSubscriptionStateFilterPropertyStrictContract
            messageSubscriptionState,
    @JsonProperty("lastUpdatedDate")
        @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate,
    @JsonProperty("messageName") @Nullable GeneratedStringFilterPropertyStrictContract messageName,
    @JsonProperty("correlationKey")
        @Nullable GeneratedStringFilterPropertyStrictContract correlationKey,
    @JsonProperty("tenantId") @Nullable GeneratedStringFilterPropertyStrictContract tenantId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedMessageSubscriptionKeyFilterPropertyStrictContract messageSubscriptionKey;
    private GeneratedProcessDefinitionKeyFilterPropertyStrictContract processDefinitionKey;
    private GeneratedStringFilterPropertyStrictContract processDefinitionId;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedStringFilterPropertyStrictContract elementId;
    private GeneratedElementInstanceKeyFilterPropertyStrictContract elementInstanceKey;
    private GeneratedMessageSubscriptionStateFilterPropertyStrictContract messageSubscriptionState;
    private GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate;
    private GeneratedStringFilterPropertyStrictContract messageName;
    private GeneratedStringFilterPropertyStrictContract correlationKey;
    private GeneratedStringFilterPropertyStrictContract tenantId;

    private Builder() {}

    @Override
    public OptionalStep messageSubscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            messageSubscriptionKey) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      return this;
    }

    @Override
    public OptionalStep messageSubscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            messageSubscriptionKey,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionKeyFilterPropertyStrictContract>
            policy) {
      this.messageSubscriptionKey =
          policy.apply(messageSubscriptionKey, Fields.MESSAGE_SUBSCRIPTION_KEY, null);
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
    public OptionalStep messageSubscriptionState(
        final @Nullable GeneratedMessageSubscriptionStateFilterPropertyStrictContract
            messageSubscriptionState) {
      this.messageSubscriptionState = messageSubscriptionState;
      return this;
    }

    @Override
    public OptionalStep messageSubscriptionState(
        final @Nullable GeneratedMessageSubscriptionStateFilterPropertyStrictContract
            messageSubscriptionState,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionStateFilterPropertyStrictContract>
            policy) {
      this.messageSubscriptionState =
          policy.apply(messageSubscriptionState, Fields.MESSAGE_SUBSCRIPTION_STATE, null);
      return this;
    }

    @Override
    public OptionalStep lastUpdatedDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate) {
      this.lastUpdatedDate = lastUpdatedDate;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.lastUpdatedDate = policy.apply(lastUpdatedDate, Fields.LAST_UPDATED_DATE, null);
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
    public GeneratedMessageSubscriptionFilterStrictContract build() {
      return new GeneratedMessageSubscriptionFilterStrictContract(
          this.messageSubscriptionKey,
          this.processDefinitionKey,
          this.processDefinitionId,
          this.processInstanceKey,
          this.elementId,
          this.elementInstanceKey,
          this.messageSubscriptionState,
          this.lastUpdatedDate,
          this.messageName,
          this.correlationKey,
          this.tenantId);
    }
  }

  public interface OptionalStep {
    OptionalStep messageSubscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            messageSubscriptionKey);

    OptionalStep messageSubscriptionKey(
        final @Nullable GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
            messageSubscriptionKey,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable GeneratedProcessDefinitionKeyFilterPropertyStrictContract
            processDefinitionKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessDefinitionKeyFilterPropertyStrictContract>
            policy);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable GeneratedStringFilterPropertyStrictContract processDefinitionId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

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

    OptionalStep messageSubscriptionState(
        final @Nullable GeneratedMessageSubscriptionStateFilterPropertyStrictContract
            messageSubscriptionState);

    OptionalStep messageSubscriptionState(
        final @Nullable GeneratedMessageSubscriptionStateFilterPropertyStrictContract
            messageSubscriptionState,
        final ContractPolicy.FieldPolicy<
                GeneratedMessageSubscriptionStateFilterPropertyStrictContract>
            policy);

    OptionalStep lastUpdatedDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate);

    OptionalStep lastUpdatedDate(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract lastUpdatedDate,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName);

    OptionalStep messageName(
        final @Nullable GeneratedStringFilterPropertyStrictContract messageName,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey);

    OptionalStep correlationKey(
        final @Nullable GeneratedStringFilterPropertyStrictContract correlationKey,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep tenantId(final @Nullable GeneratedStringFilterPropertyStrictContract tenantId);

    OptionalStep tenantId(
        final @Nullable GeneratedStringFilterPropertyStrictContract tenantId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    GeneratedMessageSubscriptionFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef MESSAGE_SUBSCRIPTION_KEY =
        ContractPolicy.field("MessageSubscriptionFilter", "messageSubscriptionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("MessageSubscriptionFilter", "processDefinitionKey");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("MessageSubscriptionFilter", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("MessageSubscriptionFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("MessageSubscriptionFilter", "elementId");
    public static final ContractPolicy.FieldRef ELEMENT_INSTANCE_KEY =
        ContractPolicy.field("MessageSubscriptionFilter", "elementInstanceKey");
    public static final ContractPolicy.FieldRef MESSAGE_SUBSCRIPTION_STATE =
        ContractPolicy.field("MessageSubscriptionFilter", "messageSubscriptionState");
    public static final ContractPolicy.FieldRef LAST_UPDATED_DATE =
        ContractPolicy.field("MessageSubscriptionFilter", "lastUpdatedDate");
    public static final ContractPolicy.FieldRef MESSAGE_NAME =
        ContractPolicy.field("MessageSubscriptionFilter", "messageName");
    public static final ContractPolicy.FieldRef CORRELATION_KEY =
        ContractPolicy.field("MessageSubscriptionFilter", "correlationKey");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("MessageSubscriptionFilter", "tenantId");

    private Fields() {}
  }
}
