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
public record GeneratedDocumentLinkStrictContract(String url, String expiresAt) {

  public GeneratedDocumentLinkStrictContract {
    Objects.requireNonNull(url, "url is required and must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static UrlStep builder() {
    return new Builder();
  }

  public static final class Builder implements UrlStep, ExpiresAtStep, OptionalStep {
    private String url;
    private ContractPolicy.FieldPolicy<String> urlPolicy;
    private String expiresAt;
    private ContractPolicy.FieldPolicy<String> expiresAtPolicy;

    private Builder() {}

    @Override
    public ExpiresAtStep url(final String url, final ContractPolicy.FieldPolicy<String> policy) {
      this.url = url;
      this.urlPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep expiresAt(
        final String expiresAt, final ContractPolicy.FieldPolicy<String> policy) {
      this.expiresAt = expiresAt;
      this.expiresAtPolicy = policy;
      return this;
    }

    @Override
    public GeneratedDocumentLinkStrictContract build() {
      return new GeneratedDocumentLinkStrictContract(
          applyRequiredPolicy(this.url, this.urlPolicy, Fields.URL),
          applyRequiredPolicy(this.expiresAt, this.expiresAtPolicy, Fields.EXPIRES_AT));
    }
  }

  public interface UrlStep {
    ExpiresAtStep url(final String url, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ExpiresAtStep {
    OptionalStep expiresAt(final String expiresAt, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedDocumentLinkStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef URL = ContractPolicy.field("DocumentLink", "url");
    public static final ContractPolicy.FieldRef EXPIRES_AT =
        ContractPolicy.field("DocumentLink", "expiresAt");

    private Fields() {}
  }
}
