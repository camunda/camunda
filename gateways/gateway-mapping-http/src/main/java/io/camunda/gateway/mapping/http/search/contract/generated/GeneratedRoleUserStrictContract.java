/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedRoleUserStrictContract(@JsonProperty("username") String username) {

  public GeneratedRoleUserStrictContract {
    Objects.requireNonNull(username, "No username provided.");
  }

  public static UsernameStep builder() {
    return new Builder();
  }

  public static final class Builder implements UsernameStep, OptionalStep {
    private String username;

    private Builder() {}

    @Override
    public OptionalStep username(final String username) {
      this.username = username;
      return this;
    }

    @Override
    public GeneratedRoleUserStrictContract build() {
      return new GeneratedRoleUserStrictContract(this.username);
    }
  }

  public interface UsernameStep {
    OptionalStep username(final String username);
  }

  public interface OptionalStep {
    GeneratedRoleUserStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("RoleUserResult", "username");

    private Fields() {}
  }
}
