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
import org.springframework.lang.Nullable;

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static CamundaDocumentTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements CamundaDocumentTypeStep, StoreIdStep, DocumentIdStep, MetadataStep, OptionalStep {
    private String camundaDocumentType;
    private ContractPolicy.FieldPolicy<String> camundaDocumentTypePolicy;
    private String storeId;
    private ContractPolicy.FieldPolicy<String> storeIdPolicy;
    private String documentId;
    private ContractPolicy.FieldPolicy<String> documentIdPolicy;
    private String contentHash;
    private Object metadata;
    private ContractPolicy.FieldPolicy<Object> metadataPolicy;

    private Builder() {}

    @Override
    public StoreIdStep camundaDocumentType(
        final String camundaDocumentType, final ContractPolicy.FieldPolicy<String> policy) {
      this.camundaDocumentType = camundaDocumentType;
      this.camundaDocumentTypePolicy = policy;
      return this;
    }

    @Override
    public DocumentIdStep storeId(
        final String storeId, final ContractPolicy.FieldPolicy<String> policy) {
      this.storeId = storeId;
      this.storeIdPolicy = policy;
      return this;
    }

    @Override
    public MetadataStep documentId(
        final String documentId, final ContractPolicy.FieldPolicy<String> policy) {
      this.documentId = documentId;
      this.documentIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep metadata(
        final Object metadata, final ContractPolicy.FieldPolicy<Object> policy) {
      this.metadata = metadata;
      this.metadataPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep contentHash(final String contentHash) {
      this.contentHash = contentHash;
      return this;
    }

    @Override
    public OptionalStep contentHash(
        final String contentHash, final ContractPolicy.FieldPolicy<String> policy) {
      this.contentHash = policy.apply(contentHash, Fields.CONTENT_HASH, null);
      return this;
    }

    @Override
    public GeneratedDocumentReferenceStrictContract build() {
      return new GeneratedDocumentReferenceStrictContract(
          applyRequiredPolicy(
              this.camundaDocumentType,
              this.camundaDocumentTypePolicy,
              Fields.CAMUNDA_DOCUMENT_TYPE),
          applyRequiredPolicy(this.storeId, this.storeIdPolicy, Fields.STORE_ID),
          applyRequiredPolicy(this.documentId, this.documentIdPolicy, Fields.DOCUMENT_ID),
          this.contentHash,
          coerceMetadata(applyRequiredPolicy(this.metadata, this.metadataPolicy, Fields.METADATA)));
    }
  }

  public interface CamundaDocumentTypeStep {
    StoreIdStep camundaDocumentType(
        final String camundaDocumentType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StoreIdStep {
    DocumentIdStep storeId(final String storeId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DocumentIdStep {
    MetadataStep documentId(
        final String documentId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface MetadataStep {
    OptionalStep metadata(final Object metadata, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep contentHash(final String contentHash);

    OptionalStep contentHash(
        final String contentHash, final ContractPolicy.FieldPolicy<String> policy);

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
