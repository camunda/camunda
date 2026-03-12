/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationStrictContract(
    String ownerId,
    io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
    io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
    @Nullable String resourceId,
    @Nullable String resourcePropertyName,
    java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes,
    String authorizationKey) {

  public GeneratedAuthorizationStrictContract {
    Objects.requireNonNull(ownerId, "ownerId is required and must not be null");
    Objects.requireNonNull(ownerType, "ownerType is required and must not be null");
    Objects.requireNonNull(resourceType, "resourceType is required and must not be null");
    Objects.requireNonNull(permissionTypes, "permissionTypes is required and must not be null");
    Objects.requireNonNull(authorizationKey, "authorizationKey is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<String> ownerIdPolicy;
    private io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum>
        ownerTypePolicy;
    private io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ResourceTypeEnum>
        resourceTypePolicy;
    private String resourceId;
    private String resourcePropertyName;
    private java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes;
    private ContractPolicy.FieldPolicy<
            java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
        permissionTypesPolicy;
    private Object authorizationKey;
    private ContractPolicy.FieldPolicy<Object> authorizationKeyPolicy;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(
        final String ownerId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ownerId = ownerId;
      this.ownerIdPolicy = policy;
      return this;
    }

    @Override
    public ResourceTypeStep ownerType(
        final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum> policy) {
      this.ownerType = ownerType;
      this.ownerTypePolicy = policy;
      return this;
    }

    @Override
    public PermissionTypesStep resourceType(
        final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ResourceTypeEnum>
            policy) {
      this.resourceType = resourceType;
      this.resourceTypePolicy = policy;
      return this;
    }

    @Override
    public AuthorizationKeyStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
            policy) {
      this.permissionTypes = permissionTypes;
      this.permissionTypesPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep authorizationKey(
        final Object authorizationKey, final ContractPolicy.FieldPolicy<Object> policy) {
      this.authorizationKey = authorizationKey;
      this.authorizationKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public OptionalStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceId = policy.apply(resourceId, Fields.RESOURCE_ID, null);
      return this;
    }

    @Override
    public OptionalStep resourcePropertyName(final String resourcePropertyName) {
      this.resourcePropertyName = resourcePropertyName;
      return this;
    }

    @Override
    public OptionalStep resourcePropertyName(
        final String resourcePropertyName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourcePropertyName =
          policy.apply(resourcePropertyName, Fields.RESOURCE_PROPERTY_NAME, null);
      return this;
    }

    @Override
    public GeneratedAuthorizationStrictContract build() {
      return new GeneratedAuthorizationStrictContract(
          applyRequiredPolicy(this.ownerId, this.ownerIdPolicy, Fields.OWNER_ID),
          applyRequiredPolicy(this.ownerType, this.ownerTypePolicy, Fields.OWNER_TYPE),
          applyRequiredPolicy(this.resourceType, this.resourceTypePolicy, Fields.RESOURCE_TYPE),
          this.resourceId,
          this.resourcePropertyName,
          applyRequiredPolicy(
              this.permissionTypes, this.permissionTypesPolicy, Fields.PERMISSION_TYPES),
          coerceAuthorizationKey(
              applyRequiredPolicy(
                  this.authorizationKey, this.authorizationKeyPolicy, Fields.AUTHORIZATION_KEY)));
    }
  }

  public interface OwnerIdStep {
    OwnerTypeStep ownerId(final String ownerId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OwnerTypeStep {
    ResourceTypeStep ownerType(
        final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum> policy);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(
        final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ResourceTypeEnum>
            policy);
  }

  public interface PermissionTypesStep {
    AuthorizationKeyStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
            policy);
  }

  public interface AuthorizationKeyStep {
    OptionalStep authorizationKey(
        final Object authorizationKey, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    OptionalStep resourceId(final String resourceId);

    OptionalStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep resourcePropertyName(final String resourcePropertyName);

    OptionalStep resourcePropertyName(
        final String resourcePropertyName, final ContractPolicy.FieldPolicy<String> policy);

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
