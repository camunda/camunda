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
public record GeneratedBatchOperationItemFilterStrictContract(
    @Nullable Object batchOperationKey,
    @Nullable Object itemKey,
    @Nullable Object processInstanceKey,
    @Nullable String state,
    @Nullable Object operationType) {

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
    private Object batchOperationKey;
    private Object itemKey;
    private Object processInstanceKey;
    private String state;
    private Object operationType;

    private Builder() {}

    @Override
    public OptionalStep batchOperationKey(final Object batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationKey(
        final Object batchOperationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperationKey = policy.apply(batchOperationKey, Fields.BATCH_OPERATION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep itemKey(final Object itemKey) {
      this.itemKey = itemKey;
      return this;
    }

    @Override
    public OptionalStep itemKey(
        final Object itemKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.itemKey = policy.apply(itemKey, Fields.ITEM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep state(final String state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(final String state, final ContractPolicy.FieldPolicy<String> policy) {
      this.state = policy.apply(state, Fields.STATE, null);
      return this;
    }

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
    public GeneratedBatchOperationItemFilterStrictContract build() {
      return new GeneratedBatchOperationItemFilterStrictContract(
          this.batchOperationKey,
          this.itemKey,
          this.processInstanceKey,
          this.state,
          this.operationType);
    }
  }

  public interface OptionalStep {
    OptionalStep batchOperationKey(final Object batchOperationKey);

    OptionalStep batchOperationKey(
        final Object batchOperationKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep itemKey(final Object itemKey);

    OptionalStep itemKey(final Object itemKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep state(final String state);

    OptionalStep state(final String state, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep operationType(final Object operationType);

    OptionalStep operationType(
        final Object operationType, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedBatchOperationItemFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("BatchOperationItemFilter", "batchOperationKey");
    public static final ContractPolicy.FieldRef ITEM_KEY =
        ContractPolicy.field("BatchOperationItemFilter", "itemKey");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("BatchOperationItemFilter", "processInstanceKey");
    public static final ContractPolicy.FieldRef STATE =
        ContractPolicy.field("BatchOperationItemFilter", "state");
    public static final ContractPolicy.FieldRef OPERATION_TYPE =
        ContractPolicy.field("BatchOperationItemFilter", "operationType");

    private Fields() {}
  }
}
