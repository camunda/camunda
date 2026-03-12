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
public record GeneratedProblemDetailStrictContract(
    String type, String title, Integer status, String detail, String instance) {

  public GeneratedProblemDetailStrictContract {
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(title, "title is required and must not be null");
    Objects.requireNonNull(status, "status is required and must not be null");
    Objects.requireNonNull(detail, "detail is required and must not be null");
    Objects.requireNonNull(instance, "instance is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TypeStep, TitleStep, StatusStep, DetailStep, InstanceStep, OptionalStep {
    private String type;
    private ContractPolicy.FieldPolicy<String> typePolicy;
    private String title;
    private ContractPolicy.FieldPolicy<String> titlePolicy;
    private Integer status;
    private ContractPolicy.FieldPolicy<Integer> statusPolicy;
    private String detail;
    private ContractPolicy.FieldPolicy<String> detailPolicy;
    private String instance;
    private ContractPolicy.FieldPolicy<String> instancePolicy;

    private Builder() {}

    @Override
    public TitleStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = type;
      this.typePolicy = policy;
      return this;
    }

    @Override
    public StatusStep title(final String title, final ContractPolicy.FieldPolicy<String> policy) {
      this.title = title;
      this.titlePolicy = policy;
      return this;
    }

    @Override
    public DetailStep status(
        final Integer status, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.status = status;
      this.statusPolicy = policy;
      return this;
    }

    @Override
    public InstanceStep detail(
        final String detail, final ContractPolicy.FieldPolicy<String> policy) {
      this.detail = detail;
      this.detailPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep instance(
        final String instance, final ContractPolicy.FieldPolicy<String> policy) {
      this.instance = instance;
      this.instancePolicy = policy;
      return this;
    }

    @Override
    public GeneratedProblemDetailStrictContract build() {
      return new GeneratedProblemDetailStrictContract(
          applyRequiredPolicy(this.type, this.typePolicy, Fields.TYPE),
          applyRequiredPolicy(this.title, this.titlePolicy, Fields.TITLE),
          applyRequiredPolicy(this.status, this.statusPolicy, Fields.STATUS),
          applyRequiredPolicy(this.detail, this.detailPolicy, Fields.DETAIL),
          applyRequiredPolicy(this.instance, this.instancePolicy, Fields.INSTANCE));
    }
  }

  public interface TypeStep {
    TitleStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TitleStep {
    StatusStep title(final String title, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface StatusStep {
    DetailStep status(final Integer status, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface DetailStep {
    InstanceStep detail(final String detail, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface InstanceStep {
    OptionalStep instance(final String instance, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedProblemDetailStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("ProblemDetail", "type");
    public static final ContractPolicy.FieldRef TITLE =
        ContractPolicy.field("ProblemDetail", "title");
    public static final ContractPolicy.FieldRef STATUS =
        ContractPolicy.field("ProblemDetail", "status");
    public static final ContractPolicy.FieldRef DETAIL =
        ContractPolicy.field("ProblemDetail", "detail");
    public static final ContractPolicy.FieldRef INSTANCE =
        ContractPolicy.field("ProblemDetail", "instance");

    private Fields() {}
  }
}
