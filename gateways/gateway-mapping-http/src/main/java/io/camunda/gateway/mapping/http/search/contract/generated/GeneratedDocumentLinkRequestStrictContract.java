/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/documents.yaml#/components/schemas/DocumentLinkRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDocumentLinkRequestStrictContract(
    @Nullable Long timeToLive
) {


  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Long timeToLive;

    private Builder() {}

    @Override
    public OptionalStep timeToLive(final @Nullable Long timeToLive) {
      this.timeToLive = timeToLive;
      return this;
    }

    @Override
    public OptionalStep timeToLive(final @Nullable Long timeToLive, final ContractPolicy.FieldPolicy<Long> policy) {
      this.timeToLive = policy.apply(timeToLive, Fields.TIME_TO_LIVE, null);
      return this;
    }

    @Override
    public GeneratedDocumentLinkRequestStrictContract build() {
      return new GeneratedDocumentLinkRequestStrictContract(
          this.timeToLive);
    }
  }

  public interface OptionalStep {
  OptionalStep timeToLive(final @Nullable Long timeToLive);

  OptionalStep timeToLive(final @Nullable Long timeToLive, final ContractPolicy.FieldPolicy<Long> policy);


    GeneratedDocumentLinkRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef TIME_TO_LIVE = ContractPolicy.field("DocumentLinkRequest", "timeToLive");

    private Fields() {}
  }


}
