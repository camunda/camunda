/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/UserTaskAuditLogFilter
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
public record GeneratedUserTaskAuditLogFilterStrictContract(
    @JsonProperty("operationType")
        @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
    @JsonProperty("result") @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
    @JsonProperty("timestamp") @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
    @JsonProperty("actorType")
        @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
    @JsonProperty("actorId") @Nullable GeneratedStringFilterPropertyStrictContract actorId) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedOperationTypeFilterPropertyStrictContract operationType;
    private GeneratedAuditLogResultFilterPropertyStrictContract result;
    private GeneratedDateTimeFilterPropertyStrictContract timestamp;
    private GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType;
    private GeneratedStringFilterPropertyStrictContract actorId;

    private Builder() {}

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedOperationTypeFilterPropertyStrictContract>
            policy) {
      this.operationType = policy.apply(operationType, Fields.OPERATION_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogResultFilterPropertyStrictContract>
            policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy) {
      this.timestamp = policy.apply(timestamp, Fields.TIMESTAMP, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogActorTypeFilterPropertyStrictContract>
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
    public GeneratedUserTaskAuditLogFilterStrictContract build() {
      return new GeneratedUserTaskAuditLogFilterStrictContract(
          this.operationType, this.result, this.timestamp, this.actorType, this.actorId);
    }
  }

  public interface OptionalStep {
    OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType);

    OptionalStep operationType(
        final @Nullable GeneratedOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedOperationTypeFilterPropertyStrictContract>
            policy);

    OptionalStep result(final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result);

    OptionalStep result(
        final @Nullable GeneratedAuditLogResultFilterPropertyStrictContract result,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogResultFilterPropertyStrictContract>
            policy);

    OptionalStep timestamp(final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp);

    OptionalStep timestamp(
        final @Nullable GeneratedDateTimeFilterPropertyStrictContract timestamp,
        final ContractPolicy.FieldPolicy<GeneratedDateTimeFilterPropertyStrictContract> policy);

    OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType);

    OptionalStep actorType(
        final @Nullable GeneratedAuditLogActorTypeFilterPropertyStrictContract actorType,
        final ContractPolicy.FieldPolicy<GeneratedAuditLogActorTypeFilterPropertyStrictContract>
            policy);

    OptionalStep actorId(final @Nullable GeneratedStringFilterPropertyStrictContract actorId);

    OptionalStep actorId(
        final @Nullable GeneratedStringFilterPropertyStrictContract actorId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

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
