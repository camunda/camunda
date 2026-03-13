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
public record GeneratedCorrelatedMessageSubscriptionFilterStrictContract(
    @Nullable Object correlationKey,
    @Nullable Object correlationTime,
    @Nullable Object elementId,
    @Nullable Object elementInstanceKey,
    @Nullable Object messageKey,
    @Nullable Object messageName,
    @Nullable Object partitionId,
    @Nullable Object processDefinitionId,
    @Nullable Object processDefinitionKey,
    @Nullable Object processInstanceKey,
    @Nullable Object subscriptionKey,
    @Nullable Object tenantId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object correlationKey;
    private Object correlationTime;
    private Object elementId;
    private Object elementInstanceKey;
    private Object messageKey;
    private Object messageName;
    private Object partitionId;
    private Object processDefinitionId;
    private Object processDefinitionKey;
    private Object processInstanceKey;
    private Object subscriptionKey;
    private Object tenantId;

    private Builder() {}

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
    public OptionalStep correlationTime(final @Nullable Object correlationTime) {
      this.correlationTime = correlationTime;
      return this;
    }

    @Override
    public OptionalStep correlationTime(
        final @Nullable Object correlationTime, final ContractPolicy.FieldPolicy<Object> policy) {
      this.correlationTime = policy.apply(correlationTime, Fields.CORRELATION_TIME, null);
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
    public OptionalStep messageKey(final @Nullable Object messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    @Override
    public OptionalStep messageKey(
        final @Nullable Object messageKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageKey = policy.apply(messageKey, Fields.MESSAGE_KEY, null);
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
    public OptionalStep partitionId(final @Nullable Object partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public OptionalStep partitionId(
        final @Nullable Object partitionId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.partitionId = policy.apply(partitionId, Fields.PARTITION_ID, null);
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
    public OptionalStep subscriptionKey(final @Nullable Object subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    @Override
    public OptionalStep subscriptionKey(
        final @Nullable Object subscriptionKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.subscriptionKey = policy.apply(subscriptionKey, Fields.SUBSCRIPTION_KEY, null);
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
    OptionalStep correlationKey(final @Nullable Object correlationKey);

    OptionalStep correlationKey(
        final @Nullable Object correlationKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep correlationTime(final @Nullable Object correlationTime);

    OptionalStep correlationTime(
        final @Nullable Object correlationTime, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementId(final @Nullable Object elementId);

    OptionalStep elementId(
        final @Nullable Object elementId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep elementInstanceKey(final @Nullable Object elementInstanceKey);

    OptionalStep elementInstanceKey(
        final @Nullable Object elementInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep messageKey(final @Nullable Object messageKey);

    OptionalStep messageKey(
        final @Nullable Object messageKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep messageName(final @Nullable Object messageName);

    OptionalStep messageName(
        final @Nullable Object messageName, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep partitionId(final @Nullable Object partitionId);

    OptionalStep partitionId(
        final @Nullable Object partitionId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionId(final @Nullable Object processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep subscriptionKey(final @Nullable Object subscriptionKey);

    OptionalStep subscriptionKey(
        final @Nullable Object subscriptionKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep tenantId(final @Nullable Object tenantId);

    OptionalStep tenantId(
        final @Nullable Object tenantId, final ContractPolicy.FieldPolicy<Object> policy);

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
