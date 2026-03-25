/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/deployments.yaml#/components/schemas/DeleteResourceRequest
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
public record GeneratedDeleteResourceRequestStrictContract(
    @JsonProperty("operationReference") @Nullable Long operationReference,
    @JsonProperty("deleteHistory") @Nullable Boolean deleteHistory) {

  public GeneratedDeleteResourceRequestStrictContract {
    if (operationReference != null)
      if (operationReference < 1L)
        throw new IllegalArgumentException(
            "The value for operationReference is '" + operationReference + "' but must be > 0.");
    if (deleteHistory == null) deleteHistory = false;
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Long operationReference;
    private Boolean deleteHistory;

    private Builder() {}

    @Override
    public OptionalStep operationReference(final @Nullable Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public OptionalStep deleteHistory(final @Nullable Boolean deleteHistory) {
      this.deleteHistory = deleteHistory;
      return this;
    }

    @Override
    public OptionalStep deleteHistory(
        final @Nullable Boolean deleteHistory, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.deleteHistory = policy.apply(deleteHistory, Fields.DELETE_HISTORY, null);
      return this;
    }

    @Override
    public GeneratedDeleteResourceRequestStrictContract build() {
      return new GeneratedDeleteResourceRequestStrictContract(
          this.operationReference, this.deleteHistory);
    }
  }

  public interface OptionalStep {
    OptionalStep operationReference(final @Nullable Long operationReference);

    OptionalStep operationReference(
        final @Nullable Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep deleteHistory(final @Nullable Boolean deleteHistory);

    OptionalStep deleteHistory(
        final @Nullable Boolean deleteHistory, final ContractPolicy.FieldPolicy<Boolean> policy);

    GeneratedDeleteResourceRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("DeleteResourceRequest", "operationReference");
    public static final ContractPolicy.FieldRef DELETE_HISTORY =
        ContractPolicy.field("DeleteResourceRequest", "deleteHistory");

    private Fields() {}
  }
}
