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
public record GeneratedGroupClientStrictContract(String clientId) {

  public GeneratedGroupClientStrictContract {
    Objects.requireNonNull(clientId, "clientId is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ClientIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ClientIdStep, OptionalStep {
    private String clientId;
    private ContractPolicy.FieldPolicy<String> clientIdPolicy;

    private Builder() {}

    @Override
    public OptionalStep clientId(
        final String clientId, final ContractPolicy.FieldPolicy<String> policy) {
      this.clientId = clientId;
      this.clientIdPolicy = policy;
      return this;
    }

    @Override
    public GeneratedGroupClientStrictContract build() {
      return new GeneratedGroupClientStrictContract(
          applyRequiredPolicy(this.clientId, this.clientIdPolicy, Fields.CLIENT_ID));
    }
  }

  public interface ClientIdStep {
    OptionalStep clientId(final String clientId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedGroupClientStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLIENT_ID =
        ContractPolicy.field("GroupClientResult", "clientId");

    private Fields() {}
  }
}
