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
public record GeneratedDocumentLinkStrictContract(String url, String expiresAt) {

  public GeneratedDocumentLinkStrictContract {
    Objects.requireNonNull(url, "url is required and must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt is required and must not be null");
  }

  public static UrlStep builder() {
    return new Builder();
  }

  public static final class Builder implements UrlStep, ExpiresAtStep, OptionalStep {
    private String url;
    private String expiresAt;

    private Builder() {}

    @Override
    public ExpiresAtStep url(final String url) {
      this.url = url;
      return this;
    }

    @Override
    public OptionalStep expiresAt(final String expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    @Override
    public GeneratedDocumentLinkStrictContract build() {
      return new GeneratedDocumentLinkStrictContract(this.url, this.expiresAt);
    }
  }

  public interface UrlStep {
    ExpiresAtStep url(final String url);
  }

  public interface ExpiresAtStep {
    OptionalStep expiresAt(final String expiresAt);
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
