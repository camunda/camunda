/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskAuditLogFilterStrictContract(
    @Nullable Object operationType,
    @Nullable Object result,
    @Nullable Object timestamp,
    @Nullable Object actorType,
    @Nullable Object actorId) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object operationType;
    private Object result;
    private Object timestamp;
    private Object actorType;
    private Object actorId;

    private Builder() {}

    @Override
    public OptionalStep operationType(final Object operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final Object operationType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep result(final Object result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final Object result, final ContractPolicy.FieldPolicy<Object> policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public OptionalStep timestamp(final Object timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final Object timestamp, final ContractPolicy.FieldPolicy<Object> policy) {
      this.timestamp = policy.apply(timestamp, Fields.TIMESTAMP, null);
      return this;
    }

    @Override
    public OptionalStep actorType(final Object actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final Object actorType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final Object actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final Object actorId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public GeneratedUserTaskAuditLogFilterStrictContract build() {
      return new GeneratedUserTaskAuditLogFilterStrictContract(
          this.operationType, this.result, this.timestamp, this.actorType, this.actorId);
    }
  }

  public interface OptionalStep {
    OptionalStep operationType(final Object operationType);

    OptionalStep operationType(
        final Object operationType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep result(final Object result);

    OptionalStep result(final Object result, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep timestamp(final Object timestamp);

    OptionalStep timestamp(final Object timestamp, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep actorType(final Object actorType);

    OptionalStep actorType(final Object actorType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep actorId(final Object actorId);

    OptionalStep actorId(final Object actorId, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedUserTaskAuditLogFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("UserTaskAuditLogFilter", "operationType");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("UserTaskAuditLogFilter", "result");
    public static final ContractPolicy.FieldRef TIMESTAMP =
        ContractPolicy.field("UserTaskAuditLogFilter", "timestamp");
    public static final ContractPolicy.FieldRef ACTOR_TYPE =
        ContractPolicy.field("UserTaskAuditLogFilter", "actorType");
    public static final ContractPolicy.FieldRef ACTOR_ID =
        ContractPolicy.field("UserTaskAuditLogFilter", "actorId");

    private Fields() {}
  }
}
