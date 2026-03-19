/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/roles.yaml#/components/schemas/RoleFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedRoleFilterStrictContract(
    @JsonProperty("roleId") @Nullable String roleId, @JsonProperty("name") @Nullable String name) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String roleId;
    private String name;

    private Builder() {}

    @Override
    public OptionalStep roleId(final @Nullable String roleId) {
      this.roleId = roleId;
      return this;
    }

    @Override
    public OptionalStep roleId(
        final @Nullable String roleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.roleId = policy.apply(roleId, Fields.ROLE_ID, null);
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public GeneratedRoleFilterStrictContract build() {
      return new GeneratedRoleFilterStrictContract(this.roleId, this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep roleId(final @Nullable String roleId);

    OptionalStep roleId(
        final @Nullable String roleId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedRoleFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ROLE_ID =
        ContractPolicy.field("RoleFilter", "roleId");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("RoleFilter", "name");

    private Fields() {}
  }
}
