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
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationResponseStrictContract(
    String batchOperationKey,
    io.camunda.gateway.protocol.model.BatchOperationStateEnum state,
    io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
    @Nullable String startDate,
    @Nullable String endDate,
    @Nullable io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
    @Nullable String actorId,
    Integer operationsTotalCount,
    Integer operationsFailedCount,
    Integer operationsCompletedCount,
    java.util.List<GeneratedBatchOperationErrorStrictContract> errors) {

  public GeneratedBatchOperationResponseStrictContract {
    Objects.requireNonNull(batchOperationKey, "batchOperationKey is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
    Objects.requireNonNull(
        batchOperationType, "batchOperationType is required and must not be null");
    Objects.requireNonNull(
        operationsTotalCount, "operationsTotalCount is required and must not be null");
    Objects.requireNonNull(
        operationsFailedCount, "operationsFailedCount is required and must not be null");
    Objects.requireNonNull(
        operationsCompletedCount, "operationsCompletedCount is required and must not be null");
    Objects.requireNonNull(errors, "errors is required and must not be null");
  }

  public static java.util.List<GeneratedBatchOperationErrorStrictContract> coerceErrors(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "errors must be a List of GeneratedBatchOperationErrorStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedBatchOperationErrorStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedBatchOperationErrorStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "errors must contain only GeneratedBatchOperationErrorStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static BatchOperationKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements BatchOperationKeyStep,
          StateStep,
          BatchOperationTypeStep,
          OperationsTotalCountStep,
          OperationsFailedCountStep,
          OperationsCompletedCountStep,
          ErrorsStep,
          OptionalStep {
    private String batchOperationKey;
    private ContractPolicy.FieldPolicy<String> batchOperationKeyPolicy;
    private io.camunda.gateway.protocol.model.BatchOperationStateEnum state;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationStateEnum>
        statePolicy;
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
        batchOperationTypePolicy;
    private String startDate;
    private String endDate;
    private io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType;
    private String actorId;
    private Integer operationsTotalCount;
    private ContractPolicy.FieldPolicy<Integer> operationsTotalCountPolicy;
    private Integer operationsFailedCount;
    private ContractPolicy.FieldPolicy<Integer> operationsFailedCountPolicy;
    private Integer operationsCompletedCount;
    private ContractPolicy.FieldPolicy<Integer> operationsCompletedCountPolicy;
    private Object errors;
    private ContractPolicy.FieldPolicy<Object> errorsPolicy;

    private Builder() {}

    @Override
    public StateStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.batchOperationKey = batchOperationKey;
      this.batchOperationKeyPolicy = policy;
      return this;
    }

    @Override
    public BatchOperationTypeStep state(
        final io.camunda.gateway.protocol.model.BatchOperationStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationStateEnum>
            policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public OperationsTotalCountStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy) {
      this.batchOperationType = batchOperationType;
      this.batchOperationTypePolicy = policy;
      return this;
    }

    @Override
    public OperationsFailedCountStep operationsTotalCount(
        final Integer operationsTotalCount, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.operationsTotalCount = operationsTotalCount;
      this.operationsTotalCountPolicy = policy;
      return this;
    }

    @Override
    public OperationsCompletedCountStep operationsFailedCount(
        final Integer operationsFailedCount, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.operationsFailedCount = operationsFailedCount;
      this.operationsFailedCountPolicy = policy;
      return this;
    }

    @Override
    public ErrorsStep operationsCompletedCount(
        final Integer operationsCompletedCount, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.operationsCompletedCount = operationsCompletedCount;
      this.operationsCompletedCountPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep errors(
        final Object errors, final ContractPolicy.FieldPolicy<Object> policy) {
      this.errors = errors;
      this.errorsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep startDate(final String startDate) {
      this.startDate = startDate;
      return this;
    }

    @Override
    public OptionalStep startDate(
        final String startDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.startDate = policy.apply(startDate, Fields.START_DATE, null);
      return this;
    }

    @Override
    public OptionalStep endDate(final String endDate) {
      this.endDate = endDate;
      return this;
    }

    @Override
    public OptionalStep endDate(
        final String endDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.endDate = policy.apply(endDate, Fields.END_DATE, null);
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType) {
      this.actorType = actorType;
      return this;
    }

    @Override
    public OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy) {
      this.actorType = policy.apply(actorType, Fields.ACTOR_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep actorId(final String actorId) {
      this.actorId = actorId;
      return this;
    }

    @Override
    public OptionalStep actorId(
        final String actorId, final ContractPolicy.FieldPolicy<String> policy) {
      this.actorId = policy.apply(actorId, Fields.ACTOR_ID, null);
      return this;
    }

    @Override
    public GeneratedBatchOperationResponseStrictContract build() {
      return new GeneratedBatchOperationResponseStrictContract(
          applyRequiredPolicy(
              this.batchOperationKey, this.batchOperationKeyPolicy, Fields.BATCH_OPERATION_KEY),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          applyRequiredPolicy(
              this.batchOperationType, this.batchOperationTypePolicy, Fields.BATCH_OPERATION_TYPE),
          this.startDate,
          this.endDate,
          this.actorType,
          this.actorId,
          applyRequiredPolicy(
              this.operationsTotalCount,
              this.operationsTotalCountPolicy,
              Fields.OPERATIONS_TOTAL_COUNT),
          applyRequiredPolicy(
              this.operationsFailedCount,
              this.operationsFailedCountPolicy,
              Fields.OPERATIONS_FAILED_COUNT),
          applyRequiredPolicy(
              this.operationsCompletedCount,
              this.operationsCompletedCountPolicy,
              Fields.OPERATIONS_COMPLETED_COUNT),
          coerceErrors(applyRequiredPolicy(this.errors, this.errorsPolicy, Fields.ERRORS)));
    }
  }

  public interface BatchOperationKeyStep {
    StateStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StateStep {
    BatchOperationTypeStep state(
        final io.camunda.gateway.protocol.model.BatchOperationStateEnum state,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationStateEnum>
            policy);
  }

  public interface BatchOperationTypeStep {
    OperationsTotalCountStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy);
  }

  public interface OperationsTotalCountStep {
    OperationsFailedCountStep operationsTotalCount(
        final Integer operationsTotalCount, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OperationsFailedCountStep {
    OperationsCompletedCountStep operationsFailedCount(
        final Integer operationsFailedCount, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface OperationsCompletedCountStep {
    ErrorsStep operationsCompletedCount(
        final Integer operationsCompletedCount, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ErrorsStep {
    OptionalStep errors(final Object errors, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep startDate(final String startDate);

    OptionalStep startDate(final String startDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep endDate(final String endDate);

    OptionalStep endDate(final String endDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep actorType(final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType);

    OptionalStep actorType(
        final io.camunda.gateway.protocol.model.AuditLogActorTypeEnum actorType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.AuditLogActorTypeEnum>
            policy);

    OptionalStep actorId(final String actorId);

    OptionalStep actorId(final String actorId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedBatchOperationResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("BatchOperationResponse", "batchOperationKey");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("BatchOperationResponse", "state");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_TYPE =
        ContractPolicy.field("BatchOperationResponse", "batchOperationType");
    public static final ContractPolicy.FieldRef START_DATE =
        ContractPolicy.field("BatchOperationResponse", "startDate");
    public static final ContractPolicy.FieldRef END_DATE =
        ContractPolicy.field("BatchOperationResponse", "endDate");
    public static final ContractPolicy.FieldRef ACTOR_TYPE =
        ContractPolicy.field("BatchOperationResponse", "actorType");
    public static final ContractPolicy.FieldRef ACTOR_ID =
        ContractPolicy.field("BatchOperationResponse", "actorId");
    public static final ContractPolicy.FieldRef OPERATIONS_TOTAL_COUNT =
        ContractPolicy.field("BatchOperationResponse", "operationsTotalCount");
    public static final ContractPolicy.FieldRef OPERATIONS_FAILED_COUNT =
        ContractPolicy.field("BatchOperationResponse", "operationsFailedCount");
    public static final ContractPolicy.FieldRef OPERATIONS_COMPLETED_COUNT =
        ContractPolicy.field("BatchOperationResponse", "operationsCompletedCount");
    public static final ContractPolicy.FieldRef ERRORS =
        ContractPolicy.field("BatchOperationResponse", "errors");

    private Fields() {}
  }
}
