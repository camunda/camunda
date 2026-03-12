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
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationCreatedStrictContract(
    String batchOperationKey,
    io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType) {

  public GeneratedBatchOperationCreatedStrictContract {
    Objects.requireNonNull(batchOperationKey, "batchOperationKey is required and must not be null");
    Objects.requireNonNull(
        batchOperationType, "batchOperationType is required and must not be null");
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
      implements BatchOperationKeyStep, BatchOperationTypeStep, OptionalStep {
    private String batchOperationKey;
    private ContractPolicy.FieldPolicy<String> batchOperationKeyPolicy;
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
        batchOperationTypePolicy;

    private Builder() {}

    @Override
    public BatchOperationTypeStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.batchOperationKey = batchOperationKey;
      this.batchOperationKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy) {
      this.batchOperationType = batchOperationType;
      this.batchOperationTypePolicy = policy;
      return this;
    }

    @Override
    public GeneratedBatchOperationCreatedStrictContract build() {
      return new GeneratedBatchOperationCreatedStrictContract(
          applyRequiredPolicy(
              this.batchOperationKey, this.batchOperationKeyPolicy, Fields.BATCH_OPERATION_KEY),
          applyRequiredPolicy(
              this.batchOperationType, this.batchOperationTypePolicy, Fields.BATCH_OPERATION_TYPE));
    }
  }

  public interface BatchOperationKeyStep {
    BatchOperationTypeStep batchOperationKey(
        final String batchOperationKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface BatchOperationTypeStep {
    OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.BatchOperationTypeEnum>
            policy);
  }

  public interface OptionalStep {
    GeneratedBatchOperationCreatedStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BATCH_OPERATION_KEY =
        ContractPolicy.field("BatchOperationCreatedResult", "batchOperationKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION_TYPE =
        ContractPolicy.field("BatchOperationCreatedResult", "batchOperationType");

    private Fields() {}
  }
}
