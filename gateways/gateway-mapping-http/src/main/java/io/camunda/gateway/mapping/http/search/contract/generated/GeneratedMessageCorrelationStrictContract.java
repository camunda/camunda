/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/MessageCorrelationResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMessageCorrelationStrictContract(
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("messageKey") String messageKey,
    @JsonProperty("processInstanceKey") String processInstanceKey) {

  public GeneratedMessageCorrelationStrictContract {
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(messageKey, "No messageKey provided.");
    Objects.requireNonNull(processInstanceKey, "No processInstanceKey provided.");
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

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TenantIdStep, MessageKeyStep, ProcessInstanceKeyStep, OptionalStep {
    private String tenantId;
    private Object messageKey;
    private Object processInstanceKey;

    private Builder() {}

    @Override
    public MessageKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep messageKey(final Object messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public GeneratedMessageCorrelationStrictContract build() {
      return new GeneratedMessageCorrelationStrictContract(
          this.tenantId,
          coerceMessageKey(this.messageKey),
          coerceProcessInstanceKey(this.processInstanceKey));
    }
  }

  public interface TenantIdStep {
    MessageKeyStep tenantId(final String tenantId);
  }

  public interface MessageKeyStep {
    ProcessInstanceKeyStep messageKey(final Object messageKey);
  }

  public interface ProcessInstanceKeyStep {
    OptionalStep processInstanceKey(final Object processInstanceKey);
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
