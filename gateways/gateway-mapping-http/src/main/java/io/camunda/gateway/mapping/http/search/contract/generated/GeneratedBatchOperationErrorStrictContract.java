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
public record GeneratedBatchOperationErrorStrictContract(
    Integer partitionId, String type, String message) {

  public GeneratedBatchOperationErrorStrictContract {
    Objects.requireNonNull(partitionId, "partitionId is required and must not be null");
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(message, "message is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static PartitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements PartitionIdStep, TypeStep, MessageStep, OptionalStep {
    private Integer partitionId;
    private ContractPolicy.FieldPolicy<Integer> partitionIdPolicy;
    private String type;
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private String message;
    private ContractPolicy.FieldPolicy<String> messagePolicy;

    private Builder() {}

    @Override
    public TypeStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.partitionId = partitionId;
      this.partitionIdPolicy = policy;
      return this;
    }

    @Override
    public MessageStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep message(
        final String message, final ContractPolicy.FieldPolicy<String> policy) {
      this.message = message;
      this.messagePolicy = policy;
      return this;
    }

    @Override
    public GeneratedBatchOperationErrorStrictContract build() {
      return new GeneratedBatchOperationErrorStrictContract(
          applyRequiredPolicy(this.partitionId, this.partitionIdPolicy, Fields.PARTITION_ID),
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          applyRequiredPolicy(this.message, this.messagePolicy, Fields.MESSAGE));
    }
  }

  public interface PartitionIdStep {
    TypeStep partitionId(
        final Integer partitionId, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface TypeStep {
    MessageStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MessageStep {
    OptionalStep message(final String message, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedBatchOperationErrorStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PARTITION_ID =
        ContractPolicy.field("BatchOperationError", "partitionId");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("BatchOperationError", "type");
    public static final ContractPolicy.FieldRef MESSAGE =
        ContractPolicy.field("BatchOperationError", "message");

    private Fields() {}
  }
}
