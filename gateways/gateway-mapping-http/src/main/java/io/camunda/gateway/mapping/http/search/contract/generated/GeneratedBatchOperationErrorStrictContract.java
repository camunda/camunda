/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/batch-operations.yaml#/components/schemas/BatchOperationError
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBatchOperationErrorStrictContract(
    @JsonProperty("partitionId") Integer partitionId,
    @JsonProperty("type") String type,
    @JsonProperty("message") String message) {

  public GeneratedBatchOperationErrorStrictContract {
    Objects.requireNonNull(partitionId, "No partitionId provided.");
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(message, "No message provided.");
  }

  public static PartitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements PartitionIdStep, TypeStep, MessageStep, OptionalStep {
    private Integer partitionId;
    private String type;
    private String message;

    private Builder() {}

    @Override
    public TypeStep partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public MessageStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep message(final String message) {
      this.message = message;
      return this;
    }

    @Override
    public GeneratedBatchOperationErrorStrictContract build() {
      return new GeneratedBatchOperationErrorStrictContract(
          this.partitionId, this.type, this.message);
    }
  }

  public interface PartitionIdStep {
    TypeStep partitionId(final Integer partitionId);
  }

  public interface TypeStep {
    MessageStep type(final String type);
  }

  public interface MessageStep {
    OptionalStep message(final String message);
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
