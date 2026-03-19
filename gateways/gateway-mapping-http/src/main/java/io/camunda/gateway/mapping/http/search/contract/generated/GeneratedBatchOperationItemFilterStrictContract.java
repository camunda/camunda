/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/batch-operations.yaml#/components/schemas/BatchOperationItemFilter
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
public record GeneratedBatchOperationItemFilterStrictContract(
    @JsonProperty("batchOperationKey")
        @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey,
    @JsonProperty("itemKey") @Nullable GeneratedBasicStringFilterPropertyStrictContract itemKey,
    @JsonProperty("processInstanceKey")
        @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
    @JsonProperty("state")
        @Nullable GeneratedBatchOperationItemStateFilterPropertyStrictContract state,
    @JsonProperty("operationType")
        @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedBasicStringFilterPropertyStrictContract batchOperationKey;
    private GeneratedBasicStringFilterPropertyStrictContract itemKey;
    private GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey;
    private GeneratedBatchOperationItemStateFilterPropertyStrictContract state;
    private GeneratedBatchOperationTypeFilterPropertyStrictContract operationType;

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
    public OptionalStep itemKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract itemKey) {
      this.itemKey = itemKey;
      return this;
    }

    @Override
    public OptionalStep itemKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract itemKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy) {
      this.itemKey = policy.apply(itemKey, Fields.ITEM_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract
            processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedBatchOperationItemStateFilterPropertyStrictContract state) {
      this.state = state;
      return this;
    }

    @Override
    public OptionalStep state(
        final @Nullable GeneratedBatchOperationItemStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<
                GeneratedBatchOperationItemStateFilterPropertyStrictContract>
            policy) {
      this.state = policy.apply(state, Fields.STATE, null);
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
    OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey);

    OptionalStep batchOperationKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract batchOperationKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep itemKey(final @Nullable GeneratedBasicStringFilterPropertyStrictContract itemKey);

    OptionalStep itemKey(
        final @Nullable GeneratedBasicStringFilterPropertyStrictContract itemKey,
        final ContractPolicy.FieldPolicy<GeneratedBasicStringFilterPropertyStrictContract> policy);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable GeneratedProcessInstanceKeyFilterPropertyStrictContract processInstanceKey,
        final ContractPolicy.FieldPolicy<GeneratedProcessInstanceKeyFilterPropertyStrictContract>
            policy);

    OptionalStep state(
        final @Nullable GeneratedBatchOperationItemStateFilterPropertyStrictContract state);

    OptionalStep state(
        final @Nullable GeneratedBatchOperationItemStateFilterPropertyStrictContract state,
        final ContractPolicy.FieldPolicy<
                GeneratedBatchOperationItemStateFilterPropertyStrictContract>
            policy);

    OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType);

    OptionalStep operationType(
        final @Nullable GeneratedBatchOperationTypeFilterPropertyStrictContract operationType,
        final ContractPolicy.FieldPolicy<GeneratedBatchOperationTypeFilterPropertyStrictContract>
            policy);

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
