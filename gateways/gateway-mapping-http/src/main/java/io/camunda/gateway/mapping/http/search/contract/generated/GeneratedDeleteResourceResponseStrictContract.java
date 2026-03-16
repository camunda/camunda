/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/deployments.yaml#/components/schemas/DeleteResourceResponse
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
public record GeneratedDeleteResourceResponseStrictContract(
    String resourceKey,
    @Nullable GeneratedBatchOperationCreatedStrictContract batchOperation
) {

  public GeneratedDeleteResourceResponseStrictContract {
    Objects.requireNonNull(resourceKey, "resourceKey is required and must not be null");
  }

  public static GeneratedBatchOperationCreatedStrictContract coerceBatchOperation(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedBatchOperationCreatedStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "batchOperation must be a GeneratedBatchOperationCreatedStrictContract, but was " + value.getClass().getName());
  }



  public static ResourceKeyStep builder() {
    return new Builder();
  }

  public static final class Builder implements ResourceKeyStep, OptionalStep {
    private String resourceKey;
    private Object batchOperation;

    private Builder() {}

    @Override
    public OptionalStep resourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep batchOperation(final @Nullable GeneratedBatchOperationCreatedStrictContract batchOperation) {
      this.batchOperation = batchOperation;
      return this;
    }

    @Override
    public OptionalStep batchOperation(final @Nullable Object batchOperation) {
      this.batchOperation = batchOperation;
      return this;
    }

    public Builder batchOperation(final @Nullable GeneratedBatchOperationCreatedStrictContract batchOperation, final ContractPolicy.FieldPolicy<GeneratedBatchOperationCreatedStrictContract> policy) {
      this.batchOperation = policy.apply(batchOperation, Fields.BATCH_OPERATION, null);
      return this;
    }

    @Override
    public OptionalStep batchOperation(final @Nullable Object batchOperation, final ContractPolicy.FieldPolicy<Object> policy) {
      this.batchOperation = policy.apply(batchOperation, Fields.BATCH_OPERATION, null);
      return this;
    }

    @Override
    public GeneratedDeleteResourceResponseStrictContract build() {
      return new GeneratedDeleteResourceResponseStrictContract(
          this.resourceKey,
          coerceBatchOperation(this.batchOperation));
    }
  }

  public interface ResourceKeyStep {
    OptionalStep resourceKey(final String resourceKey);
  }

  public interface OptionalStep {
  OptionalStep batchOperation(final @Nullable GeneratedBatchOperationCreatedStrictContract batchOperation);

  OptionalStep batchOperation(final @Nullable Object batchOperation);

  OptionalStep batchOperation(final @Nullable GeneratedBatchOperationCreatedStrictContract batchOperation, final ContractPolicy.FieldPolicy<GeneratedBatchOperationCreatedStrictContract> policy);

  OptionalStep batchOperation(final @Nullable Object batchOperation, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedDeleteResourceResponseStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef RESOURCE_KEY = ContractPolicy.field("DeleteResourceResponse", "resourceKey");
    public static final ContractPolicy.FieldRef BATCH_OPERATION = ContractPolicy.field("DeleteResourceResponse", "batchOperation");

    private Fields() {}
  }


}
