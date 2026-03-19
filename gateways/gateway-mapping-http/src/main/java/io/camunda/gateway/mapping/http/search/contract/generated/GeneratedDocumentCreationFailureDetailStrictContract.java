/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/documents.yaml#/components/schemas/DocumentCreationFailureDetail
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
public record GeneratedDocumentCreationFailureDetailStrictContract(
    @JsonProperty("fileName") String fileName,
    @JsonProperty("status") Integer status,
    @JsonProperty("title") String title,
    @JsonProperty("detail") String detail) {

  public GeneratedDocumentCreationFailureDetailStrictContract {
    Objects.requireNonNull(fileName, "No fileName provided.");
    Objects.requireNonNull(status, "No status provided.");
    Objects.requireNonNull(title, "No title provided.");
    Objects.requireNonNull(detail, "No detail provided.");
  }

  public static FileNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements FileNameStep, StatusStep, TitleStep, DetailStep, OptionalStep {
    private String fileName;
    private Integer status;
    private String title;
    private String detail;

    private Builder() {}

    @Override
    public StatusStep fileName(final String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public TitleStep status(final Integer status) {
      this.status = status;
      return this;
    }

    @Override
    public DetailStep title(final String title) {
      this.title = title;
      return this;
    }

    @Override
    public OptionalStep detail(final String detail) {
      this.detail = detail;
      return this;
    }

    @Override
    public GeneratedDocumentCreationFailureDetailStrictContract build() {
      return new GeneratedDocumentCreationFailureDetailStrictContract(
          this.fileName, this.status, this.title, this.detail);
    }
  }

  public interface FileNameStep {
    StatusStep fileName(final String fileName);
  }

  public interface StatusStep {
    TitleStep status(final Integer status);
  }

  public interface TitleStep {
    DetailStep title(final String title);
  }

  public interface DetailStep {
    OptionalStep detail(final String detail);
  }

  public interface OptionalStep {
    GeneratedDocumentCreationFailureDetailStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef FILE_NAME =
        ContractPolicy.field("DocumentCreationFailureDetail", "fileName");
    public static final ContractPolicy.FieldRef STATUS =
        ContractPolicy.field("DocumentCreationFailureDetail", "status");
    public static final ContractPolicy.FieldRef TITLE =
        ContractPolicy.field("DocumentCreationFailureDetail", "title");
    public static final ContractPolicy.FieldRef DETAIL =
        ContractPolicy.field("DocumentCreationFailureDetail", "detail");

    private Fields() {}
  }
}
