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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentMetadataStrictContract(
    @Nullable String contentType,
    @Nullable String fileName,
    @Nullable String expiresAt,
    @Nullable Long size,
    @Nullable String processDefinitionId,
    @Nullable String processInstanceKey,
    @Nullable java.util.Map<String, Object> customProperties) {

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    public OptionalStep contentType(final String contentType) {
      this.contentType = contentType;
      return this;
    }

    @Override
    public OptionalStep contentType(
        final String contentType, final ContractPolicy.FieldPolicy<String> policy) {
      this.contentType = policy.apply(contentType, Fields.CONTENT_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep fileName(final String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public OptionalStep fileName(
        final String fileName, final ContractPolicy.FieldPolicy<String> policy) {
      this.fileName = policy.apply(fileName, Fields.FILE_NAME, null);
      return this;
    }

    @Override
    public OptionalStep expiresAt(final String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public OptionalStep expiresAt(
        final String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = policy.apply(expiresAt, Fields.EXPIRES_AT, null);
      return this;
    }

    @Override
    public OptionalStep size(final Long size) {
      this.size = size;
      return this;
    }

    @Override
    public OptionalStep size(final Long size, final ContractPolicy.FieldPolicy<Long> policy) {
      this.size = policy.apply(size, Fields.SIZE, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final Object processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep customProperties(final java.util.Map<String, Object> customProperties) {
      this.customProperties = customProperties;
      return this;
    }

    @Override
    public OptionalStep customProperties(
        final java.util.Map<String, Object> customProperties,
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
    OptionalStep contentType(final String contentType);

    OptionalStep contentType(
        final String contentType, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep fileName(final String fileName);

    OptionalStep fileName(final String fileName, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep expiresAt(final String expiresAt);

    OptionalStep expiresAt(final String expiresAt, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep size(final Long size);

    OptionalStep size(final Long size, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep processDefinitionId(final String processDefinitionId);

    OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep customProperties(final java.util.Map<String, Object> customProperties);

    OptionalStep customProperties(
        final java.util.Map<String, Object> customProperties,
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
