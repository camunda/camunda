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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentCreationFailureDetailStrictContract(
    String fileName, Integer status, String title, String detail) {

  public GeneratedDocumentCreationFailureDetailStrictContract {
    Objects.requireNonNull(fileName, "fileName is required and must not be null");
    Objects.requireNonNull(status, "status is required and must not be null");
    Objects.requireNonNull(title, "title is required and must not be null");
    Objects.requireNonNull(detail, "detail is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static FileNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements FileNameStep, StatusStep, TitleStep, DetailStep, OptionalStep {
    private String fileName;
    private ContractPolicy.FieldPolicy<String> fileNamePolicy;
    private Integer status;
    private ContractPolicy.FieldPolicy<Integer> statusPolicy;
    private String title;
    private ContractPolicy.FieldPolicy<String> titlePolicy;
    private String detail;
    private ContractPolicy.FieldPolicy<String> detailPolicy;

    private Builder() {}

    @Override
    public StatusStep fileName(
        final String fileName, final ContractPolicy.FieldPolicy<String> policy) {
      this.fileName = fileName;
      this.fileNamePolicy = policy;
      return this;
    }

    @Override
    public TitleStep status(
        final Integer status, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.status = status;
      this.statusPolicy = policy;
      return this;
    }

    @Override
    public DetailStep title(final String title, final ContractPolicy.FieldPolicy<String> policy) {
      this.title = title;
      this.titlePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep detail(
        final String detail, final ContractPolicy.FieldPolicy<String> policy) {
      this.detail = detail;
      this.detailPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDocumentCreationFailureDetailStrictContract build() {
      return new GeneratedDocumentCreationFailureDetailStrictContract(
          applyRequiredPolicy(this.fileName, this.fileNamePolicy, Fields.FILE_NAME),
          applyRequiredPolicy(this.status, this.statusPolicy, Fields.STATUS),
          applyRequiredPolicy(this.title, this.titlePolicy, Fields.TITLE),
          applyRequiredPolicy(this.detail, this.detailPolicy, Fields.DETAIL));
    }
  }

  public interface FileNameStep {
    StatusStep fileName(final String fileName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StatusStep {
    TitleStep status(final Integer status, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface TitleStep {
    DetailStep title(final String title, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface DetailStep {
    OptionalStep detail(final String detail, final ContractPolicy.FieldPolicy<String> policy);
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
