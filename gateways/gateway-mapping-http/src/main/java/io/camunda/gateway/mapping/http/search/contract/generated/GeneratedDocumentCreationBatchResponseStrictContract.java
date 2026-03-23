/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/documents.yaml#/components/schemas/DocumentCreationBatchResponse
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentCreationBatchResponseStrictContract(
    @JsonProperty("failedDocuments")
        java.util.@Nullable List<GeneratedDocumentCreationFailureDetailStrictContract>
            failedDocuments,
    @JsonProperty("createdDocuments")
        java.util.@Nullable List<GeneratedDocumentReferenceStrictContract> createdDocuments) {

  public static java.util.List<GeneratedDocumentCreationFailureDetailStrictContract>
      coerceFailedDocuments(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "failedDocuments must be a List of GeneratedDocumentCreationFailureDetailStrictContract, but was "
              + value.getClass().getName());
    }

    final var result =
        new ArrayList<GeneratedDocumentCreationFailureDetailStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedDocumentCreationFailureDetailStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "failedDocuments must contain only GeneratedDocumentCreationFailureDetailStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static java.util.List<GeneratedDocumentReferenceStrictContract> coerceCreatedDocuments(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "createdDocuments must be a List of GeneratedDocumentReferenceStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedDocumentReferenceStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedDocumentReferenceStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "createdDocuments must contain only GeneratedDocumentReferenceStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object failedDocuments;
    private Object createdDocuments;

    private Builder() {}

    @Override
    public OptionalStep failedDocuments(
        final java.util.@Nullable List<GeneratedDocumentCreationFailureDetailStrictContract>
            failedDocuments) {
      this.failedDocuments = failedDocuments;
      return this;
    }

    @Override
    public OptionalStep failedDocuments(final @Nullable Object failedDocuments) {
      this.failedDocuments = failedDocuments;
      return this;
    }

    public Builder failedDocuments(
        final java.util.@Nullable List<GeneratedDocumentCreationFailureDetailStrictContract>
            failedDocuments,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedDocumentCreationFailureDetailStrictContract>>
            policy) {
      this.failedDocuments = policy.apply(failedDocuments, Fields.FAILED_DOCUMENTS, null);
      return this;
    }

    @Override
    public OptionalStep failedDocuments(
        final @Nullable Object failedDocuments, final ContractPolicy.FieldPolicy<Object> policy) {
      this.failedDocuments = policy.apply(failedDocuments, Fields.FAILED_DOCUMENTS, null);
      return this;
    }

    @Override
    public OptionalStep createdDocuments(
        final java.util.@Nullable List<GeneratedDocumentReferenceStrictContract> createdDocuments) {
      this.createdDocuments = createdDocuments;
      return this;
    }

    @Override
    public OptionalStep createdDocuments(final @Nullable Object createdDocuments) {
      this.createdDocuments = createdDocuments;
      return this;
    }

    public Builder createdDocuments(
        final java.util.@Nullable List<GeneratedDocumentReferenceStrictContract> createdDocuments,
        final ContractPolicy.FieldPolicy<java.util.List<GeneratedDocumentReferenceStrictContract>>
            policy) {
      this.createdDocuments = policy.apply(createdDocuments, Fields.CREATED_DOCUMENTS, null);
      return this;
    }

    @Override
    public OptionalStep createdDocuments(
        final @Nullable Object createdDocuments, final ContractPolicy.FieldPolicy<Object> policy) {
      this.createdDocuments = policy.apply(createdDocuments, Fields.CREATED_DOCUMENTS, null);
      return this;
    }

    @Override
    public GeneratedDocumentCreationBatchResponseStrictContract build() {
      return new GeneratedDocumentCreationBatchResponseStrictContract(
          coerceFailedDocuments(this.failedDocuments),
          coerceCreatedDocuments(this.createdDocuments));
    }
  }

  public interface OptionalStep {
    OptionalStep failedDocuments(
        final java.util.@Nullable List<GeneratedDocumentCreationFailureDetailStrictContract>
            failedDocuments);

    OptionalStep failedDocuments(final @Nullable Object failedDocuments);

    OptionalStep failedDocuments(
        final java.util.@Nullable List<GeneratedDocumentCreationFailureDetailStrictContract>
            failedDocuments,
        final ContractPolicy.FieldPolicy<
                java.util.List<GeneratedDocumentCreationFailureDetailStrictContract>>
            policy);

    OptionalStep failedDocuments(
        final @Nullable Object failedDocuments, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep createdDocuments(
        final java.util.@Nullable List<GeneratedDocumentReferenceStrictContract> createdDocuments);

    OptionalStep createdDocuments(final @Nullable Object createdDocuments);

    OptionalStep createdDocuments(
        final java.util.@Nullable List<GeneratedDocumentReferenceStrictContract> createdDocuments,
        final ContractPolicy.FieldPolicy<java.util.List<GeneratedDocumentReferenceStrictContract>>
            policy);

    OptionalStep createdDocuments(
        final @Nullable Object createdDocuments, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedDocumentCreationBatchResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FAILED_DOCUMENTS =
        ContractPolicy.field("DocumentCreationBatchResponse", "failedDocuments");
    public static final ContractPolicy.FieldRef CREATED_DOCUMENTS =
        ContractPolicy.field("DocumentCreationBatchResponse", "createdDocuments");

    private Fields() {}
  }
}
