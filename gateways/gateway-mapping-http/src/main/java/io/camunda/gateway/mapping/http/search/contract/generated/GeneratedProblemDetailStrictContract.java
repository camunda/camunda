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

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
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

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TypeStep, TitleStep, StatusStep, DetailStep, InstanceStep, OptionalStep {
    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;

    private Builder() {}

    @Override
    public TitleStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public StatusStep title(final String title) {
      this.title = title;
      return this;
    }

    @Override
    public DetailStep status(final Integer status) {
      this.status = status;
      return this;
    }

    @Override
    public InstanceStep detail(final String detail) {
      this.detail = detail;
      return this;
    }

    @Override
    public OptionalStep instance(final String instance) {
      this.instance = instance;
      return this;
    }

    @Override
    public GeneratedProblemDetailStrictContract build() {
      return new GeneratedProblemDetailStrictContract(
          this.type, this.title, this.status, this.detail, this.instance);
    }
  }

  public interface TypeStep {
    TitleStep type(final String type);
  }

  public interface TitleStep {
    StatusStep title(final String title);
  }

  public interface StatusStep {
    DetailStep status(final Integer status);
  }

  public interface DetailStep {
    InstanceStep detail(final String detail);
  }

  public interface InstanceStep {
    OptionalStep instance(final String instance);
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
