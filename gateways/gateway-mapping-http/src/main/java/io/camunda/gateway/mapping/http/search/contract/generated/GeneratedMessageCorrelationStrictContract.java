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
public record GeneratedMessageCorrelationStrictContract(
    String tenantId, String messageKey, String processInstanceKey) {

  public GeneratedMessageCorrelationStrictContract {
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(messageKey, "messageKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TenantIdStep, MessageKeyStep, ProcessInstanceKeyStep, OptionalStep {
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private Object messageKey;
    private ContractPolicy.FieldPolicy<Object> messageKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;

    private Builder() {}

    @Override
    public MessageKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep messageKey(
        final Object messageKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.messageKey = messageKey;
      this.messageKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public GeneratedMessageCorrelationStrictContract build() {
      return new GeneratedMessageCorrelationStrictContract(
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          coerceMessageKey(
              applyRequiredPolicy(this.messageKey, this.messageKeyPolicy, Fields.MESSAGE_KEY)),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)));
    }
  }

  public interface TenantIdStep {
    MessageKeyStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MessageKeyStep {
    ProcessInstanceKeyStep messageKey(
        final Object messageKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ProcessInstanceKeyStep {
    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedMessageCorrelationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("MessageCorrelationResult", "tenantId");
    public static final ContractPolicy.FieldRef MESSAGE_KEY =
        ContractPolicy.field("MessageCorrelationResult", "messageKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("MessageCorrelationResult", "processInstanceKey");

    private Fields() {}
  }
}
