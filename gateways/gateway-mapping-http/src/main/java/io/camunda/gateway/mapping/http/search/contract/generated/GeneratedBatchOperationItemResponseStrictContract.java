/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/batch-operations.yaml#/components/schemas/BatchOperationItemResponse
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationItemResponseStrictContract(
    io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType,
    String batchOperationKey,
    String itemKey,
    String processInstanceKey,
    @Nullable String rootProcessInstanceKey,
    String state,
    @Nullable String processedDate,
    @Nullable String errorMessage
) {

  public GeneratedBatchOperationItemResponseStrictContract {
    Objects.requireNonNull(operationType, "operationType is required and must not be null");
    Objects.requireNonNull(batchOperationKey, "batchOperationKey is required and must not be null");
    Objects.requireNonNull(itemKey, "itemKey is required and must not be null");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey is required and must not be null");
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



  public static OperationTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements OperationTypeStep, BatchOperationKeyStep, ItemKeyStep, ProcessInstanceKeyStep, StateStep, OptionalStep {
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType;
    private String batchOperationKey;
    private String itemKey;
    private Object processInstanceKey;
    private Object rootProcessInstanceKey;
    private String state;
    private String processedDate;
    private String errorMessage;

    private Builder() {}

    @Override
    public BatchOperationKeyStep operationType(final io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType) {
      this.operationType = operationType;
      return this;
    }

    @Override
    public ItemKeyStep batchOperationKey(final String batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public ProcessInstanceKeyStep itemKey(final String itemKey) {
      this.itemKey = itemKey;
      return this;
    }

    @Override
    public StateStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep state(final String state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.rootProcessInstanceKey = policy.apply(rootProcessInstanceKey, Fields.ROOT_PROCESS_INSTANCE_KEY, null);
      return this;
    }


    @Override
    public OptionalStep processedDate(final @Nullable String processedDate) {
      this.processedDate = processedDate;
      return this;
    }

    @Override
    public OptionalStep processedDate(final @Nullable String processedDate, final ContractPolicy.FieldPolicy<String> policy) {
      this.processedDate = policy.apply(processedDate, Fields.PROCESSED_DATE, null);
      return this;
    }


    @Override
    public OptionalStep errorMessage(final @Nullable String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public GeneratedBatchOperationItemResponseStrictContract build() {
      return new GeneratedBatchOperationItemResponseStrictContract(
          this.operationType,
          this.batchOperationKey,
          this.itemKey,
          coerceProcessInstanceKey(this.processInstanceKey),
          coerceRootProcessInstanceKey(this.rootProcessInstanceKey),
          this.state,
          this.processedDate,
          this.errorMessage);
    }
  }

  public interface OperationTypeStep {
    BatchOperationKeyStep operationType(final io.camunda.gateway.protocol.model.BatchOperationTypeEnum operationType);
  }

  public interface BatchOperationKeyStep {
    ItemKeyStep batchOperationKey(final String batchOperationKey);
  }

  public interface ItemKeyStep {
    ProcessInstanceKeyStep itemKey(final String itemKey);
  }

  public interface ProcessInstanceKeyStep {
    StateStep processInstanceKey(final Object processInstanceKey);
  }

  public interface StateStep {
    OptionalStep state(final String state);
  }

  public interface OptionalStep {
  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey);

  OptionalStep rootProcessInstanceKey(final @Nullable String rootProcessInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep rootProcessInstanceKey(final @Nullable Object rootProcessInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep processedDate(final @Nullable String processedDate);

  OptionalStep processedDate(final @Nullable String processedDate, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep errorMessage(final @Nullable String errorMessage);

  OptionalStep errorMessage(final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedBatchOperationItemResponseStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_TYPE = ContractPolicy.field("BatchOperationItemResponse", "operationType");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY = ContractPolicy.field("BatchOperationItemResponse", "batchOperationKey");
    public static final ContractPolicy.FieldRef ITEM_KEY = ContractPolicy.field("BatchOperationItemResponse", "itemKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("BatchOperationItemResponse", "processInstanceKey");
    public static final ContractPolicy.FieldRef ROOT_PROCESS_INSTANCE_KEY = ContractPolicy.field("BatchOperationItemResponse", "rootProcessInstanceKey");
    public static final ContractPolicy.FieldRef STATE = ContractPolicy.field("BatchOperationItemResponse", "state");
    public static final ContractPolicy.FieldRef PROCESSED_DATE = ContractPolicy.field("BatchOperationItemResponse", "processedDate");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE = ContractPolicy.field("BatchOperationItemResponse", "errorMessage");

    private Fields() {}
  }


}
