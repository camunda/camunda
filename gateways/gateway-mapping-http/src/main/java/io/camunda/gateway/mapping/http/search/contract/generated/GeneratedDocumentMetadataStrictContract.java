/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentMetadataStrictContract(
    @Nullable String contentType,
    @Nullable String fileName,
    @Nullable String expiresAt,
    @Nullable Long size,
    @Nullable String processDefinitionId,
    @Nullable String processInstanceKey,
    java.util.@Nullable Map<String, Object> customProperties) {

  public static String coerceProcessInstanceKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "processInstanceKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String contentType;
    private String fileName;
    private String expiresAt;
    private Long size;
    private String processDefinitionId;
    private Object processInstanceKey;
    private java.util.Map<String, Object> customProperties;

    private Builder() {}

    @Override
    public OptionalStep contentType(final @Nullable String contentType) {
      this.contentType = contentType;
      return this;
    }

    @Override
    public OptionalStep contentType(
        final @Nullable String contentType, final ContractPolicy.FieldPolicy<String> policy) {
      this.contentType = policy.apply(contentType, Fields.CONTENT_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep fileName(final @Nullable String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public OptionalStep fileName(
        final @Nullable String fileName, final ContractPolicy.FieldPolicy<String> policy) {
      this.fileName = policy.apply(fileName, Fields.FILE_NAME, null);
      return this;
    }

    @Override
    public OptionalStep expiresAt(final @Nullable String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public OptionalStep expiresAt(
        final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = policy.apply(expiresAt, Fields.EXPIRES_AT, null);
      return this;
    }

    @Override
    public OptionalStep size(final @Nullable Long size) {
      this.size = size;
      return this;
    }

    @Override
    public OptionalStep size(
        final @Nullable Long size, final ContractPolicy.FieldPolicy<Long> policy) {
      this.size = policy.apply(size, Fields.SIZE, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final @Nullable String processInstanceKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep customProperties(
        final java.util.@Nullable Map<String, Object> customProperties) {
      this.customProperties = customProperties;
      return this;
    }

    @Override
    public OptionalStep customProperties(
        final java.util.@Nullable Map<String, Object> customProperties,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.customProperties = policy.apply(customProperties, Fields.CUSTOM_PROPERTIES, null);
      return this;
    }

    @Override
    public GeneratedDocumentMetadataStrictContract build() {
      return new GeneratedDocumentMetadataStrictContract(
          this.contentType,
          this.fileName,
          this.expiresAt,
          this.size,
          this.processDefinitionId,
          coerceProcessInstanceKey(this.processInstanceKey),
          this.customProperties);
    }
  }

  public interface OptionalStep {
    OptionalStep contentType(final @Nullable String contentType);

    OptionalStep contentType(
        final @Nullable String contentType, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep fileName(final @Nullable String fileName);

    OptionalStep fileName(
        final @Nullable String fileName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep expiresAt(final @Nullable String expiresAt);

    OptionalStep expiresAt(
        final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep size(final @Nullable Long size);

    OptionalStep size(final @Nullable Long size, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep processDefinitionId(final @Nullable String processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable String processDefinitionId,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

    OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

    OptionalStep processInstanceKey(
        final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep customProperties(final java.util.@Nullable Map<String, Object> customProperties);

    OptionalStep customProperties(
        final java.util.@Nullable Map<String, Object> customProperties,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    GeneratedDocumentMetadataStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CONTENT_TYPE =
        ContractPolicy.field("DocumentMetadata", "contentType");
    public static final ContractPolicy.FieldRef FILE_NAME =
        ContractPolicy.field("DocumentMetadata", "fileName");
    public static final ContractPolicy.FieldRef EXPIRES_AT =
        ContractPolicy.field("DocumentMetadata", "expiresAt");
    public static final ContractPolicy.FieldRef SIZE =
        ContractPolicy.field("DocumentMetadata", "size");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("DocumentMetadata", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("DocumentMetadata", "processInstanceKey");
    public static final ContractPolicy.FieldRef CUSTOM_PROPERTIES =
        ContractPolicy.field("DocumentMetadata", "customProperties");

    private Fields() {}
  }
}
