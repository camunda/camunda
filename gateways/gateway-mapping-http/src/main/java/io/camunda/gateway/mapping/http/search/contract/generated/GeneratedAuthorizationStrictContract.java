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
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationStrictContract(
    @JsonProperty("ownerId") String ownerId,
    @JsonProperty("ownerType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum ownerType,
    @JsonProperty("resourceType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType,
    @JsonProperty("resourceId") @Nullable String resourceId,
    @JsonProperty("resourcePropertyName") @Nullable String resourcePropertyName,
    @JsonProperty("permissionTypes")
        java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes,
    @JsonProperty("authorizationKey") String authorizationKey) {

  public GeneratedAuthorizationStrictContract {
    Objects.requireNonNull(ownerId, "No ownerId provided.");
    Objects.requireNonNull(ownerType, "No ownerType provided.");
    Objects.requireNonNull(resourceType, "No resourceType provided.");
    Objects.requireNonNull(permissionTypes, "No permissionTypes provided.");
    Objects.requireNonNull(authorizationKey, "No authorizationKey provided.");
  }

  public static String coerceAuthorizationKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "authorizationKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static OwnerIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements OwnerIdStep,
          OwnerTypeStep,
          ResourceTypeStep,
          PermissionTypesStep,
          AuthorizationKeyStep,
          OptionalStep {
    private String ownerId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
        ownerType;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
        resourceType;
    private String resourceId;
    private String resourcePropertyName;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated.GeneratedPermissionTypeEnum>
        permissionTypes;
    private Object authorizationKey;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(final String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    @Override
    public ResourceTypeStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    @Override
    public PermissionTypesStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    @Override
    public AuthorizationKeyStep permissionTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes) {
      this.permissionTypes = permissionTypes;
      return this;
    }

    @Override
    public OptionalStep authorizationKey(final Object authorizationKey) {
      this.authorizationKey = authorizationKey;
      return this;
    }

    @Override
    public OptionalStep resourceId(final @Nullable String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public OptionalStep resourceId(
        final @Nullable String resourceId, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceId = policy.apply(resourceId, Fields.RESOURCE_ID, null);
      return this;
    }

    @Override
    public OptionalStep resourcePropertyName(final @Nullable String resourcePropertyName) {
      this.resourcePropertyName = resourcePropertyName;
      return this;
    }

    @Override
    public OptionalStep resourcePropertyName(
        final @Nullable String resourcePropertyName,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.resourcePropertyName =
          policy.apply(resourcePropertyName, Fields.RESOURCE_PROPERTY_NAME, null);
      return this;
    }

    @Override
    public GeneratedAuthorizationStrictContract build() {
      return new GeneratedAuthorizationStrictContract(
          this.ownerId,
          this.ownerType,
          this.resourceType,
          this.resourceId,
          this.resourcePropertyName,
          this.permissionTypes,
          coerceAuthorizationKey(this.authorizationKey));
    }
  }

  public interface OwnerIdStep {
    OwnerTypeStep ownerId(final String ownerId);
  }

  public interface OwnerTypeStep {
    ResourceTypeStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType);
  }

  public interface PermissionTypesStep {
    AuthorizationKeyStep permissionTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes);
  }

  public interface AuthorizationKeyStep {
    OptionalStep authorizationKey(final Object authorizationKey);
  }

  public interface OptionalStep {
    OptionalStep resourceId(final @Nullable String resourceId);

    OptionalStep resourceId(
        final @Nullable String resourceId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep resourcePropertyName(final @Nullable String resourcePropertyName);

    OptionalStep resourcePropertyName(
        final @Nullable String resourcePropertyName,
        final ContractPolicy.FieldPolicy<String> policy);

    GeneratedAuthorizationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OWNER_ID =
        ContractPolicy.field("AuthorizationResult", "ownerId");
    public static final ContractPolicy.FieldRef OWNER_TYPE =
        ContractPolicy.field("AuthorizationResult", "ownerType");
    public static final ContractPolicy.FieldRef RESOURCE_TYPE =
        ContractPolicy.field("AuthorizationResult", "resourceType");
    public static final ContractPolicy.FieldRef RESOURCE_ID =
        ContractPolicy.field("AuthorizationResult", "resourceId");
    public static final ContractPolicy.FieldRef RESOURCE_PROPERTY_NAME =
        ContractPolicy.field("AuthorizationResult", "resourcePropertyName");
    public static final ContractPolicy.FieldRef PERMISSION_TYPES =
        ContractPolicy.field("AuthorizationResult", "permissionTypes");
    public static final ContractPolicy.FieldRef AUTHORIZATION_KEY =
        ContractPolicy.field("AuthorizationResult", "authorizationKey");

    private Fields() {}
  }
}
