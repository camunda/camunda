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
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentMetadataResponseStrictContract(
    String contentType,
    String fileName,
    @Nullable String expiresAt,
    Long size,
    @Nullable String processDefinitionId,
    @Nullable String processInstanceKey,
    java.util.Map<String, Object> customProperties) {

  public GeneratedDocumentMetadataResponseStrictContract {
    Objects.requireNonNull(contentType, "contentType is required and must not be null");
    Objects.requireNonNull(fileName, "fileName is required and must not be null");
    Objects.requireNonNull(size, "size is required and must not be null");
    Objects.requireNonNull(customProperties, "customProperties is required and must not be null");
  }

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

  public static ContentTypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ContentTypeStep, FileNameStep, SizeStep, CustomPropertiesStep, OptionalStep {
    private String contentType;
    private ContractPolicy.FieldPolicy<String> contentTypePolicy;
    private String fileName;
    private ContractPolicy.FieldPolicy<String> fileNamePolicy;
    private String expiresAt;
    private Long size;
    private ContractPolicy.FieldPolicy<Long> sizePolicy;
    private String processDefinitionId;
    private Object processInstanceKey;
    private java.util.Map<String, Object> customProperties;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> customPropertiesPolicy;

    private Builder() {}

    @Override
    public FileNameStep contentType(
        final String contentType, final ContractPolicy.FieldPolicy<String> policy) {
      this.contentType = contentType;
      this.contentTypePolicy = policy;
      return this;
    }

    @Override
    public SizeStep fileName(
        final String fileName, final ContractPolicy.FieldPolicy<String> policy) {
      this.fileName = fileName;
      this.fileNamePolicy = policy;
      return this;
    }

    @Override
    public CustomPropertiesStep size(
        final Long size, final ContractPolicy.FieldPolicy<Long> policy) {
      this.size = size;
      this.sizePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep customProperties(
        final java.util.Map<String, Object> customProperties,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.customProperties = customProperties;
      this.customPropertiesPolicy = policy;
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
    public GeneratedDocumentMetadataResponseStrictContract build() {
      return new GeneratedDocumentMetadataResponseStrictContract(
          applyRequiredPolicy(this.contentType, this.contentTypePolicy, Fields.CONTENT_TYPE),
          applyRequiredPolicy(this.fileName, this.fileNamePolicy, Fields.FILE_NAME),
          this.expiresAt,
          applyRequiredPolicy(this.size, this.sizePolicy, Fields.SIZE),
          this.processDefinitionId,
          coerceProcessInstanceKey(this.processInstanceKey),
          applyRequiredPolicy(
              this.customProperties, this.customPropertiesPolicy, Fields.CUSTOM_PROPERTIES));
    }
  }

  public interface ContentTypeStep {
    FileNameStep contentType(
        final String contentType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface FileNameStep {
    SizeStep fileName(final String fileName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface SizeStep {
    CustomPropertiesStep size(final Long size, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface CustomPropertiesStep {
    OptionalStep customProperties(
        final java.util.Map<String, Object> customProperties,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface OptionalStep {
    OptionalStep expiresAt(final String expiresAt);

    OptionalStep expiresAt(final String expiresAt, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionId(final String processDefinitionId);

    OptionalStep processDefinitionId(
        final String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(final String processInstanceKey);

    OptionalStep processInstanceKey(final Object processInstanceKey);

    OptionalStep processInstanceKey(
        final String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processInstanceKey(
        final Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedDocumentMetadataResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CONTENT_TYPE =
        ContractPolicy.field("DocumentMetadataResponse", "contentType");
    public static final ContractPolicy.FieldRef FILE_NAME =
        ContractPolicy.field("DocumentMetadataResponse", "fileName");
    public static final ContractPolicy.FieldRef EXPIRES_AT =
        ContractPolicy.field("DocumentMetadataResponse", "expiresAt");
    public static final ContractPolicy.FieldRef SIZE =
        ContractPolicy.field("DocumentMetadataResponse", "size");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("DocumentMetadataResponse", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY =
        ContractPolicy.field("DocumentMetadataResponse", "processInstanceKey");
    public static final ContractPolicy.FieldRef CUSTOM_PROPERTIES =
        ContractPolicy.field("DocumentMetadataResponse", "customProperties");

    private Fields() {}
  }
}
