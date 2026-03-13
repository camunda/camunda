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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationCreatedStrictContract(
    String batchOperationKey,
    io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType) {

  public GeneratedBatchOperationCreatedStrictContract {
    Objects.requireNonNull(batchOperationKey, "batchOperationKey is required and must not be null");
    Objects.requireNonNull(
        batchOperationType, "batchOperationType is required and must not be null");
  }

  public static BatchOperationKeyStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements BatchOperationKeyStep, BatchOperationTypeStep, OptionalStep {
    private String batchOperationKey;
    private io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType;

    private Builder() {}

    @Override
    public BatchOperationTypeStep batchOperationKey(final String batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    @Override
    public OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType) {
      this.batchOperationType = batchOperationType;
      return this;
    }

    @Override
    public GeneratedBatchOperationCreatedStrictContract build() {
      return new GeneratedBatchOperationCreatedStrictContract(
          this.batchOperationKey, this.batchOperationType);
    }
  }

  public interface BatchOperationKeyStep {
    BatchOperationTypeStep batchOperationKey(final String batchOperationKey);
  }

  public interface BatchOperationTypeStep {
    OptionalStep batchOperationType(
        final io.camunda.gateway.protocol.model.BatchOperationTypeEnum batchOperationType);
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
