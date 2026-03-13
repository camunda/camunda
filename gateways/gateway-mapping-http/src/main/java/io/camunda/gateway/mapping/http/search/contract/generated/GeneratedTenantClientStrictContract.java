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
import org.jspecify.annotations.NullMarked;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTenantClientStrictContract(String clientId) {

  public GeneratedTenantClientStrictContract {
    Objects.requireNonNull(clientId, "clientId is required and must not be null");
  }

  public static ClientIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements ClientIdStep, OptionalStep {
    private String clientId;

    private Builder() {}

    @Override
    public OptionalStep clientId(final String clientId) {
      this.clientId = clientId;
      return this;
    }

    @Override
    public GeneratedTenantClientStrictContract build() {
      return new GeneratedTenantClientStrictContract(this.clientId);
    }
  }

  public interface ClientIdStep {
    OptionalStep clientId(final String clientId);
  }

  public interface OptionalStep {
    GeneratedTenantClientStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef CLIENT_ID =
        ContractPolicy.field("TenantClientResult", "clientId");

    private Fields() {}
  }
}
