/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMessageSubscriptionFilterStrictContract(
    @Nullable Object messageSubscriptionKey,
    @Nullable Object processDefinitionKey,
    @Nullable Object processDefinitionId,
    @Nullable Object processInstanceKey,
    @Nullable Object elementId,
    @Nullable Object elementInstanceKey,
    @Nullable Object messageSubscriptionState,
    @Nullable Object lastUpdatedDate,
    @Nullable Object messageName,
    @Nullable Object correlationKey,
    @Nullable Object tenantId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object messageSubscriptionKey;
    private Object processDefinitionKey;
    private Object processDefinitionId;
    private Object processInstanceKey;
    private Object elementId;
    private Object elementInstanceKey;
    private Object messageSubscriptionState;
    private Object lastUpdatedDate;
    private Object messageName;
    private Object correlationKey;
    private Object tenantId;

    private Builder() {}

    @Override
    public OptionalStep messageSubscriptionKey(final @Nullable Object messageSubscriptionKey) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      return this;
    }

    @Override
    public OptionalStep messageSubscriptionKey(
        final @Nullable Object messageSubscriptionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageSubscriptionKey =
          policy.apply(messageSubscriptionKey, Fields.MESSAGE_SUBSCRIPTION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
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
    public OptionalStep processDefinitionId(final @Nullable Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
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
    public OptionalStep elementId(final @Nullable Object elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public OptionalStep elementId(
        final @Nullable Object elementId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.elementId = policy.apply(elementId, Fields.ELEMENT_ID, null);
      return this;
    }

    @Override
    public OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
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
    public OptionalStep messageSubscriptionState(final @Nullable Object messageSubscriptionState) {
      this.messageSubscriptionState = messageSubscriptionState;
      return this;
    }

    @Override
    public OptionalStep messageSubscriptionState(
        final @Nullable Object messageSubscriptionState,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageSubscriptionState =
          policy.apply(messageSubscriptionState, Fields.MESSAGE_SUBSCRIPTION_STATE, null);
      return this;
    }

    @Override
    public OptionalStep lastUpdatedDate(final @Nullable Object lastUpdatedDate) {
      this.lastUpdatedDate = lastUpdatedDate;
      return this;
    }

    @Override
    public OptionalStep lastUpdatedDate(
        final @Nullable Object lastUpdatedDate, final ContractPolicy.FieldPolicy<Object> policy) {
      this.lastUpdatedDate = policy.apply(lastUpdatedDate, Fields.LAST_UPDATED_DATE, null);
      return this;
    }

    @Override
    public OptionalStep messageName(final @Nullable Object messageName) {
      this.messageName = messageName;
      return this;
    }

    @Override
    public OptionalStep messageName(
        final @Nullable Object messageName, final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageName = policy.apply(messageName, Fields.MESSAGE_NAME, null);
      return this;
    }

    @Override
    public OptionalStep correlationKey(final @Nullable Object correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public OptionalStep correlationKey(
        final @Nullable Object correlationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.correlationKey = policy.apply(correlationKey, Fields.CORRELATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable Object tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy) {
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
    OptionalStep messageSubscriptionKey(final @Nullable Object messageSubscriptionKey);

    OptionalStep messageSubscriptionKey(
        final @Nullable Object messageSubscriptionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionId(final @Nullable Object processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final @Nullable Object elementId);

    OptionalStep elementId(
        final @Nullable Object elementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep messageSubscriptionState(final @Nullable Object messageSubscriptionState);

    OptionalStep messageSubscriptionState(
        final @Nullable Object messageSubscriptionState,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep lastUpdatedDate(final @Nullable Object lastUpdatedDate);

    OptionalStep lastUpdatedDate(
        final @Nullable Object lastUpdatedDate, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep messageName(final @Nullable Object messageName);

    OptionalStep messageName(
        final @Nullable Object messageName, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep correlationKey(final @Nullable Object correlationKey);

    OptionalStep correlationKey(
        final @Nullable Object correlationKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final @Nullable Object tenantId);

    OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

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
