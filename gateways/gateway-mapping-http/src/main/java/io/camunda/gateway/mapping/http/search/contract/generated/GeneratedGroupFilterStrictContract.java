/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/groups.yaml#/components/schemas/GroupFilter
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
public record GeneratedGroupFilterStrictContract(
    @JsonProperty("groupId") @Nullable GeneratedStringFilterPropertyStrictContract groupId,
    @JsonProperty("name") @Nullable String name) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private GeneratedStringFilterPropertyStrictContract groupId;
    private String name;

    private Builder() {}

    @Override
    public OptionalStep groupId(
        final @Nullable GeneratedStringFilterPropertyStrictContract groupId) {
      this.groupId = groupId;
      return this;
    }

    @Override
    public OptionalStep groupId(
        final @Nullable GeneratedStringFilterPropertyStrictContract groupId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy) {
      this.groupId = policy.apply(groupId, Fields.GROUP_ID, null);
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
    public GeneratedGroupFilterStrictContract build() {
      return new GeneratedGroupFilterStrictContract(this.groupId, this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep groupId(final @Nullable GeneratedStringFilterPropertyStrictContract groupId);

    OptionalStep groupId(
        final @Nullable GeneratedStringFilterPropertyStrictContract groupId,
        final ContractPolicy.FieldPolicy<GeneratedStringFilterPropertyStrictContract> policy);

    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedGroupFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef GROUP_ID =
        ContractPolicy.field("GroupFilter", "groupId");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("GroupFilter", "name");

    private Fields() {}
  }
}
