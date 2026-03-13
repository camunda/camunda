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
public record GeneratedBatchOperationFilterStrictContract(
    @Nullable Object batchOperationKey,
    @Nullable Object operationType,
    @Nullable Object state,
    io.camunda.gateway.protocol.model.@Nullable AuditLogActorTypeEnum actorType,
    @Nullable Object actorId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object batchOperationKey;
    private Object operationType;
    private Object state;
    private io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType;
    private Object actorId;

    private Builder() {}

    @Override
    public OptionalStep batchOperationKey(final @Nullable Object batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(
        final @Nullable Object batchOperationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperationKey = policy.apply(batchOperationKey, Fields.BATCH_OPERATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep operationType(final @Nullable Object operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable Object operationType, final ContractPolicy.FieldPolicy<Object> policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep state(final @Nullable Object state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable Object state, final ContractPolicy.FieldPolicy<Object> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.@Nullable AuditLogActorTypeEnum actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.@Nullable AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final @Nullable Object actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable Object actorId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public GeneratedBatchOperationFilterStrictContract build() {
      return new GeneratedBatchOperationFilterStrictContract(
          this.batchOperationKey, this.operationType, this.state, this.actorType, this.actorId);
    }
  }

  public interface OptionalStep {
    OptionalStep batchOperationKey(final @Nullable Object batchOperationKey);

    OptionalStep batchOperationKey(
        final @Nullable Object batchOperationKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep operationType(final @Nullable Object operationType);

    OptionalStep operationType(
        final @Nullable Object operationType, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final @Nullable Object state);

    OptionalStep state(
        final @Nullable Object state, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep actorType(
        final io.camunda.gateway.protocol.model.@Nullable AuditLogActorTypeEnum actorType);

    OptionalStep actorType(
        final io.camunda.gateway.protocol.model.@Nullable AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy);

    OptionalStep actorId(final @Nullable Object actorId);

    OptionalStep actorId(
        final @Nullable Object actorId, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedBatchOperationFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("BatchOperationFilter", "batchOperationKey");
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("BatchOperationFilter", "operationType");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("BatchOperationFilter", "state");
    public static final ContractPolicy.FieldRef ACTOR_TYPE =
        ContractPolicy.field("BatchOperationFilter", "actorType");
    public static final ContractPolicy.FieldRef ACTOR_ID =
        ContractPolicy.field("BatchOperationFilter", "actorId");

    private Fields() {}
  }
}
