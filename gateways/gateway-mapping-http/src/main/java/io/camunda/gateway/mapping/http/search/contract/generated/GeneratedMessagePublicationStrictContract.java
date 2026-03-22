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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedMessagePublicationStrictContract(
    @JsonProperty("tenantId") String tenantId, @JsonProperty("messageKey") String messageKey) {

  public GeneratedMessagePublicationStrictContract {
    Objects.requireNonNull(tenantId, "No tenantId provided.");
    Objects.requireNonNull(messageKey, "No messageKey provided.");
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

  public static TenantIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements TenantIdStep, MessageKeyStep, OptionalStep {
    private String tenantId;
    private Object messageKey;

    private Builder() {}

    @Override
    public MessageKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep messageKey(final Object messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    @Override
    public GeneratedMessagePublicationStrictContract build() {
      return new GeneratedMessagePublicationStrictContract(
          this.tenantId, coerceMessageKey(this.messageKey));
    }
  }

  public interface TenantIdStep {
    MessageKeyStep tenantId(final String tenantId);
  }

  public interface MessageKeyStep {
    OptionalStep messageKey(final Object messageKey);
  }

  public interface OptionalStep {
    GeneratedMessagePublicationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("MessagePublicationResult", "tenantId");
    public static final ContractPolicy.FieldRef MESSAGE_KEY =
        ContractPolicy.field("MessagePublicationResult", "messageKey");

    private Fields() {}
  }
}
