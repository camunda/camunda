/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/documents.yaml#/components/schemas/DocumentMetadataResponse
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
public record GeneratedDocumentMetadataResponseStrictContract(
    String contentType,
    String fileName,
    @Nullable String expiresAt,
    Long size,
    @Nullable String processDefinitionId,
    @Nullable String processInstanceKey,
    java.util.Map<String, Object> customProperties
) {

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



  public static ContentTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements ContentTypeStep, FileNameStep, SizeStep, CustomPropertiesStep, OptionalStep {
    private String contentType;
    private String fileName;
    private String expiresAt;
    private Long size;
    private String processDefinitionId;
    private Object processInstanceKey;
    private java.util.Map<String, Object> customProperties;

    private Builder() {}

    @Override
    public FileNameStep contentType(final String contentType) {
      this.contentType = contentType;
      return this;
    }

    @Override
    public SizeStep fileName(final String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public CustomPropertiesStep size(final Long size) {
      this.size = size;
      return this;
    }

    @Override
    public OptionalStep customProperties(final java.util.Map<String, Object> customProperties) {
      this.customProperties = customProperties;
      return this;
    }

    @Override
    public OptionalStep expiresAt(final @Nullable String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public OptionalStep expiresAt(final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = policy.apply(expiresAt, Fields.EXPIRES_AT, null);
      return this;
    }


    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(final @Nullable String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionId = policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
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

    public Builder processInstanceKey(final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processInstanceKey = policy.apply(processInstanceKey, Fields.PROCESS_INSTANCE_KEY, null);
      return this;
    }

    @Override
    public GeneratedDocumentMetadataResponseStrictContract build() {
      return new GeneratedDocumentMetadataResponseStrictContract(
          this.contentType,
          this.fileName,
          this.expiresAt,
          this.size,
          this.processDefinitionId,
          coerceProcessInstanceKey(this.processInstanceKey),
          this.customProperties);
    }
  }

  public interface ContentTypeStep {
    FileNameStep contentType(final String contentType);
  }

  public interface FileNameStep {
    SizeStep fileName(final String fileName);
  }

  public interface SizeStep {
    CustomPropertiesStep size(final Long size);
  }

  public interface CustomPropertiesStep {
    OptionalStep customProperties(final java.util.Map<String, Object> customProperties);
  }

  public interface OptionalStep {
  OptionalStep expiresAt(final @Nullable String expiresAt);

  OptionalStep expiresAt(final @Nullable String expiresAt, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep processDefinitionId(final @Nullable String processDefinitionId);

  OptionalStep processDefinitionId(final @Nullable String processDefinitionId, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep processInstanceKey(final @Nullable String processInstanceKey);

  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey);

  OptionalStep processInstanceKey(final @Nullable String processInstanceKey, final ContractPolicy.FieldPolicy<String> policy);

  OptionalStep processInstanceKey(final @Nullable Object processInstanceKey, final ContractPolicy.FieldPolicy<Object> policy);


    GeneratedDocumentMetadataResponseStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef CONTENT_TYPE = ContractPolicy.field("DocumentMetadataResponse", "contentType");
    public static final ContractPolicy.FieldRef FILE_NAME = ContractPolicy.field("DocumentMetadataResponse", "fileName");
    public static final ContractPolicy.FieldRef EXPIRES_AT = ContractPolicy.field("DocumentMetadataResponse", "expiresAt");
    public static final ContractPolicy.FieldRef SIZE = ContractPolicy.field("DocumentMetadataResponse", "size");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID = ContractPolicy.field("DocumentMetadataResponse", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_INSTANCE_KEY = ContractPolicy.field("DocumentMetadataResponse", "processInstanceKey");
    public static final ContractPolicy.FieldRef CUSTOM_PROPERTIES = ContractPolicy.field("DocumentMetadataResponse", "customProperties");

    private Fields() {}
  }


}
