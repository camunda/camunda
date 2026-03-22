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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationFilterStrictContract(
    @JsonProperty("ownerId") @Nullable String ownerId,
    @JsonProperty("ownerType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable GeneratedOwnerTypeEnum
            ownerType,
    @JsonProperty("resourceIds") java.util.@Nullable List<String> resourceIds,
    @JsonProperty("resourcePropertyNames") java.util.@Nullable List<String> resourcePropertyNames,
    @JsonProperty("resourceType")
        io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedResourceTypeEnum
            resourceType) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String ownerId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
        ownerType;
    private java.util.List<String> resourceIds;
    private java.util.List<String> resourcePropertyNames;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
        resourceType;

    private Builder() {}

    @Override
    public OptionalStep ownerId(final @Nullable String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    @Override
    public OptionalStep ownerId(
        final @Nullable String ownerId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ownerId = policy.apply(ownerId, Fields.OWNER_ID, null);
      return this;
    }

    @Override
    public OptionalStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedOwnerTypeEnum
            ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    @Override
    public OptionalStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedOwnerTypeEnum
            ownerType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum>
            policy) {
      this.ownerType = policy.apply(ownerType, Fields.OWNER_TYPE, null);
      return this;
    }

    @Override
    public OptionalStep resourceIds(final java.util.@Nullable List<String> resourceIds) {
      this.resourceIds = resourceIds;
      return this;
    }

    @Override
    public OptionalStep resourceIds(
        final java.util.@Nullable List<String> resourceIds,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.resourceIds = policy.apply(resourceIds, Fields.RESOURCE_IDS, null);
      return this;
    }

    @Override
    public OptionalStep resourcePropertyNames(
        final java.util.@Nullable List<String> resourcePropertyNames) {
      this.resourcePropertyNames = resourcePropertyNames;
      return this;
    }

    @Override
    public OptionalStep resourcePropertyNames(
        final java.util.@Nullable List<String> resourcePropertyNames,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.resourcePropertyNames =
          policy.apply(resourcePropertyNames, Fields.RESOURCE_PROPERTY_NAMES, null);
      return this;
    }

    @Override
    public OptionalStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedResourceTypeEnum
            resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    @Override
    public OptionalStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedResourceTypeEnum
            resourceType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum>
            policy) {
      this.resourceType = policy.apply(resourceType, Fields.RESOURCE_TYPE, null);
      return this;
    }

    @Override
    public GeneratedAuthorizationFilterStrictContract build() {
      return new GeneratedAuthorizationFilterStrictContract(
          this.ownerId,
          this.ownerType,
          this.resourceIds,
          this.resourcePropertyNames,
          this.resourceType);
    }
  }

  public interface OptionalStep {
    OptionalStep ownerId(final @Nullable String ownerId);

    OptionalStep ownerId(
        final @Nullable String ownerId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedOwnerTypeEnum
            ownerType);

    OptionalStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedOwnerTypeEnum
            ownerType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum>
            policy);

    OptionalStep resourceIds(final java.util.@Nullable List<String> resourceIds);

    OptionalStep resourceIds(
        final java.util.@Nullable List<String> resourceIds,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep resourcePropertyNames(
        final java.util.@Nullable List<String> resourcePropertyNames);

    OptionalStep resourcePropertyNames(
        final java.util.@Nullable List<String> resourcePropertyNames,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);

    OptionalStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedResourceTypeEnum
            resourceType);

    OptionalStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.@Nullable
            GeneratedResourceTypeEnum
            resourceType,
        final ContractPolicy.FieldPolicy<
                io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum>
            policy);

    GeneratedAuthorizationFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OWNER_ID =
        ContractPolicy.field("AuthorizationFilter", "ownerId");
    public static final ContractPolicy.FieldRef OWNER_TYPE =
        ContractPolicy.field("AuthorizationFilter", "ownerType");
    public static final ContractPolicy.FieldRef RESOURCE_IDS =
        ContractPolicy.field("AuthorizationFilter", "resourceIds");
    public static final ContractPolicy.FieldRef RESOURCE_PROPERTY_NAMES =
        ContractPolicy.field("AuthorizationFilter", "resourcePropertyNames");
    public static final ContractPolicy.FieldRef RESOURCE_TYPE =
        ContractPolicy.field("AuthorizationFilter", "resourceType");

    private Fields() {}
  }
}
