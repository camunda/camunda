/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/groups.yaml#/components/schemas/GroupCreateResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGroupCreateStrictContract(
    @JsonProperty("groupId") String groupId,
    @JsonProperty("name") String name,
    @JsonProperty("description") @Nullable String description) {

  public GeneratedGroupCreateStrictContract {
    Objects.requireNonNull(groupId, "No groupId provided.");
    Objects.requireNonNull(name, "No name provided.");
  }

  public static GroupIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements GroupIdStep, NameStep, OptionalStep {
    private String groupId;
    private String name;
    private String description;

    private Builder() {}

    @Override
    public NameStep groupId(final String groupId) {
      this.groupId = groupId;
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep description(final @Nullable String description) {
      this.description = description;
      return this;
    }

    @Override
    public OptionalStep description(
        final @Nullable String description, final ContractPolicy.FieldPolicy<String> policy) {
      this.description = policy.apply(description, Fields.DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedGroupCreateStrictContract build() {
      return new GeneratedGroupCreateStrictContract(this.groupId, this.name, this.description);
    }
  }

  public interface GroupIdStep {
    NameStep groupId(final String groupId);
  }

  public interface NameStep {
    OptionalStep name(final String name);
  }

  public interface OptionalStep {
    OptionalStep description(final @Nullable String description);

    OptionalStep description(
        final @Nullable String description, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedGroupCreateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef GROUP_ID =
        ContractPolicy.field("GroupCreateResult", "groupId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("GroupCreateResult", "name");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("GroupCreateResult", "description");

    private Fields() {}
  }
}
