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
public record GeneratedTenantGroupStrictContract(String groupId) {

  public GeneratedTenantGroupStrictContract {
    Objects.requireNonNull(groupId, "groupId is required and must not be null");
  }

  public static GroupIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements GroupIdStep, OptionalStep {
    private String groupId;

    private Builder() {}

    @Override
    public OptionalStep groupId(final String groupId) {
      this.groupId = groupId;
      return this;
    }

    @Override
    public GeneratedTenantGroupStrictContract build() {
      return new GeneratedTenantGroupStrictContract(this.groupId);
    }
  }

  public interface GroupIdStep {
    OptionalStep groupId(final String groupId);
  }

  public interface OptionalStep {
    GeneratedTenantGroupStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef GROUP_ID =
        ContractPolicy.field("TenantGroupResult", "groupId");

    private Fields() {}
  }
}
