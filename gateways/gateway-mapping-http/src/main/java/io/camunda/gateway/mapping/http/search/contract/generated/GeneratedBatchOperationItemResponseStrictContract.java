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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationItemResponseStrictContract(
    io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType,
    String batchOperationKey,
    String itemKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String state,
    @Nullable String processedDate,
    @Nullable String errorMessage) {

  public GeneratedBatchOperationItemResponseStrictContract {
    Objects.requireNonNull(operationType, "operationType is required and must not be null");
    Objects.requireNonNull(batchOperationKey, "batchOperationKey is required and must not be null");
    Objects.requireNonNull(itemKey, "itemKey is required and must not be null");
    Objects.requireNonNull(
        processInstanceKey, "processInstanceKey is required and must not be null");
    Objects.requireNonNull(state, "state is required and must not be null");
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

  public static String coerceRootProcessInstanceKey(final Object value) {
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
        "rootProcessInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OperationTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements OperationTypeStep,
          BatchOperationKeyStep,
          ItemKeyStep,
          ProcessInstanceKeyStep,
          StateStep,
          OptionalStep {
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
        operationTypePolicy;
    private String batchOperationKey;
    private ContractPolicy.FieldPolicy<String> batchOperationKeyPolicy;
    private String itemKey;
    private ContractPolicy.FieldPolicy<String> itemKeyPolicy;
    private Object processInstanceKey;
    private ContractPolicy.FieldPolicy<Object> processInstanceKeyPolicy;
    private Object rootProcessInstanceKey;
    private String state;
    private ContractPolicy.FieldPolicy<String> statePolicy;
    private String processedDate;
    private String errorMessage;

    private Builder() {}

    @Override
    public BatchOperationKeyStep operationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy) {
      this.operationType = operationType;
      this.operationTypePolicy = policy;
      return this;
    }

    @Override
    public ItemKeyStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.batchOperationKey = batchOperationKey;
      this.batchOperationKeyPolicy = policy;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep itemKey(
        final String itemKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.itemKey = itemKey;
      this.itemKeyPolicy = policy;
      return this;
    }

    @Override
    public StateStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = processInstanceKey;
      this.processInstanceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep state(final String state, final ContractPolicy.FieldPolicy<String> policy) {
      this.state = state;
      this.statePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey =
          policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processedDate(final String processedDate) {
      this.processedDate = processedDate;
      return this;
    }

    @Override
    public OptionalStep processedDate(
        final String processedDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.processedDate = policy.apply(processedDate, Fields.PROCESSED_DATE, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedBatchOperationItemResponseStrictContract build() {
      return new GeneratedBatchOperationItemResponseStrictContract(
          applyRequiredPolicy(this.operationType, this.operationTypePolicy, Fields.OPERATION_TYPE),
          applyRequiredPolicy(
              this.batchOperationKey, this.batchOperationKeyPolicy, Fields.BATCH_OPERATION_KEY),
          applyRequiredPolicy(this.itemKey, this.itemKeyPolicy, Fields.ITEM_KEY),
          coerceProcessInstanceKey(
              applyRequiredPolicy(
                  this.processInstanceKey,
                  this.processInstanceKeyPolicy,
                  Fields.PROCESS_INSTANCE_KEY)),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          applyRequiredPolicy(this.state, this.statePolicy, Fields.STATE),
          this.processedDate,
          this.errorMessage);
    }
  }

  public interface OperationTypeStep {
    BatchOperationKeyStep operationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy);
  }

  public interface BatchOperationKeyStep {
    ItemKeyStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ItemKeyStep {
    ProcessInstanceKeyStep itemKey(
        final String itemKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ProcessInstanceKeyStep {
    StateStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface StateStep {
    OptionalStep state(final String state, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep rootProcessInstanceKey(final String rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(final Object rootProcessInstanceKey);

    OptionalStep rootProcessInstanceKey(
        final String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep rootProcessInstanceKey(
        final Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processedDate(final String processedDate);

    OptionalStep processedDate(
        final String processedDate, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep errorMessage(final String errorMessage);

    OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedBatchOperationItemResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("BatchOperationItemResponse", "operationType");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("BatchOperationItemResponse", "batchOperationKey");
    public static final ContractPolicy.FieldRef ITEM_KEY =
        ContractPolicy.field("BatchOperationItemResponse", "itemKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("BatchOperationItemResponse", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY =
        ContractPolicy.field("BatchOperationItemResponse", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("BatchOperationItemResponse", "state");
    public static final ContractPolicy.FieldRef PROCESSED_DATE =
        ContractPolicy.field("BatchOperationItemResponse", "processedDate");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("BatchOperationItemResponse", "errorMessage");

    private Fields() {}
  }
}
