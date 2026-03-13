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
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentReferenceStrictContract(
    String camundaDocumentType,
    String storeId,
    String documentId,
    @Nullable String contentHash,
    GeneratedDocumentMetadataResponseStrictContract metadata) {

  public GeneratedDocumentReferenceStrictContract {
    Objects.requireNonNull(
        camundaDocumentType, "camunda.document.type is required and must not be null");
    Objects.requireNonNull(storeId, "storeId is required and must not be null");
    Objects.requireNonNull(documentId, "documentId is required and must not be null");
    Objects.requireNonNull(metadata, "metadata is required and must not be null");
  }

  public static GeneratedDocumentMetadataResponseStrictContract coerceMetadata(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDocumentMetadataResponseStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "metadata must be a GeneratedDocumentMetadataResponseStrictContract, but was "
            + value.getClass().getName());
  }

  public static CamundaDocumentTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CamundaDocumentTypeStep, StoreIdStep, DocumentIdStep, MetadataStep, OptionalStep {
    private String camundaDocumentType;
    private String storeId;
    private String documentId;
    private String contentHash;
    private Object metadata;

    private Builder() {}

    @Override
    public StoreIdStep camundaDocumentType(final String camundaDocumentType) {
      this.camundaDocumentType = camundaDocumentType;
      return this;
    }

    @Override
    public DocumentIdStep storeId(final String storeId) {
      this.storeId = storeId;
      return this;
    }

    @Override
    public MetadataStep documentId(final String documentId) {
      this.documentId = documentId;
      return this;
    }

    @Override
    public OptionalStep metadata(final Object metadata) {
      this.metadata = metadata;
      return this;
    }

    @Override
    public OptionalStep contentHash(final @Nullable String contentHash) {
      this.contentHash = contentHash;
      return this;
    }

    @Override
    public OptionalStep contentHash(
        final @Nullable String contentHash, final ContractPolicy.FieldPolicy<String> policy) {
      this.contentHash = policy.apply(contentHash, Fields.CONTENT_HASH, null);
      return this;
    }

    @Override
    public GeneratedDocumentReferenceStrictContract build() {
      return new GeneratedDocumentReferenceStrictContract(
          this.camundaDocumentType,
          this.storeId,
          this.documentId,
          this.contentHash,
          coerceMetadata(this.metadata));
    }
  }

  public interface CamundaDocumentTypeStep {
    StoreIdStep camundaDocumentType(final String camundaDocumentType);
  }

  public interface StoreIdStep {
    DocumentIdStep storeId(final String storeId);
  }

  public interface DocumentIdStep {
    MetadataStep documentId(final String documentId);
  }

  public interface MetadataStep {
    OptionalStep metadata(final Object metadata);
  }

  public interface OptionalStep {
    OptionalStep contentHash(final @Nullable String contentHash);

    OptionalStep contentHash(
        final @Nullable String contentHash, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedDocumentReferenceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CAMUNDA_DOCUMENT_TYPE =
        ContractPolicy.field("DocumentReference", "camunda.document.type");
    public static final ContractPolicy.FieldRef STORE_ID =
        ContractPolicy.field("DocumentReference", "storeId");
    public static final ContractPolicy.FieldRef DOCUMENT_ID =
        ContractPolicy.field("DocumentReference", "documentId");
    public static final ContractPolicy.FieldRef CONTENT_HASH =
        ContractPolicy.field("DocumentReference", "contentHash");
    public static final ContractPolicy.FieldRef METADATA =
        ContractPolicy.field("DocumentReference", "metadata");

    private Fields() {}
  }
}
