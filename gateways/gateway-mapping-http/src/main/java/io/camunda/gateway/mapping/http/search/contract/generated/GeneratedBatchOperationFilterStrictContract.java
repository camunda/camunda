/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/batch-operations.yaml#/components/schemas/BatchOperationFilter
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
public record GeneratedBatchOperationFilterStrictContract(
    @JsonProperty("batchOperationKey")
        @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey,
    @JsonProperty("operationType")
        @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType,
    @JsonProperty("state") @Nullable GeneratedBatchOperationStateFilterPropertyStrictContract state,
    @JsonProperty("actorType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
    @JsonProperty("actorId") @Nullable GeneratedStringFilterPropertyStrictContract actorId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedBasicStringFilterPropertyStrictContract batchOperationKey;
    private GeneratedBatchOperationTypeFilterPropertyStrictContract operationType;
    private GeneratedBatchOperationStateFilterPropertyStrictContract state;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogActorTypeEnum
        actorType;
    private GeneratedStringFilterPropertyStrictContract actorId;

    private Builder() {}

    @Override
    public OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy) {
      this.batchOperationKey = policy.apply(batchOperationKey, Fields.BATCH_OPERATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationTypeFilterPropertyStrictContract>
            policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedBatchOperationStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedBatchOperationStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogActorTypeEnum>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
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
    OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey);

    OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType);

    OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationTypeFilterPropertyStrictContract>
            policy);

    OptionalStep state(
        final @Nullable GeneratedBatchOperationStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedBatchOperationStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationStateFilterPropertyStrictContract>
            policy);

    OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType);

    OptionalStep actorType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedAuditLogActorTypeEnum
            actorType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedAuditLogActorTypeEnum>
            policy);

    OptionalStep actorId(final @Nullable GeneratedStringFilterPropertyStrictContract actorId);

    OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

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
