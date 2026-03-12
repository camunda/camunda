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
public record GeneratedAuthorizationIdBasedRequestStrictContract(
    String ownerId,
    io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
    String resourceId,
    io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
    java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes) {

  public GeneratedAuthorizationIdBasedRequestStrictContract {
    Objects.requireNonNull(ownerId, "ownerId is required and must not be null");
    Objects.requireNonNull(ownerType, "ownerType is required and must not be null");
    Objects.requireNonNull(resourceId, "resourceId is required and must not be null");
    Objects.requireNonNull(resourceType, "resourceType is required and must not be null");
    Objects.requireNonNull(permissionTypes, "permissionTypes is required and must not be null");
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
          ResourceIdStep,
          ResourceTypeStep,
          PermissionTypesStep,
          OptionalStep {
    private String ownerId;
    private ContractPolicy.FieldPolicy<String> ownerIdPolicy;
    private io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum>
        ownerTypePolicy;
    private String resourceId;
    private ContractPolicy.FieldPolicy<String> resourceIdPolicy;
    private io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType;
    private ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ResourceTypeEnum>
        resourceTypePolicy;
    private java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes;
    private ContractPolicy.FieldPolicy<
            java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
        permissionTypesPolicy;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(
        final String ownerId, final ContractPolicy.FieldPolicy<String> policy) {
      this.ownerId = ownerId;
      this.ownerIdPolicy = policy;
      return this;
    }

    @Override
    public ResourceIdStep ownerType(
        final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum> policy) {
      this.ownerType = ownerType;
      this.ownerTypePolicy = policy;
      return this;
    }

    @Override
    public ResourceTypeStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceId = resourceId;
      this.resourceIdPolicy = policy;
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
    public OptionalStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
            policy) {
      this.permissionTypes = permissionTypes;
      this.permissionTypesPolicy = policy;
      return this;
    }

    @Override
    public GeneratedAuthorizationIdBasedRequestStrictContract build() {
      return new GeneratedAuthorizationIdBasedRequestStrictContract(
          applyRequiredPolicy(this.ownerId, this.ownerIdPolicy, Fields.OWNER_ID),
          applyRequiredPolicy(this.ownerType, this.ownerTypePolicy, Fields.OWNER_TYPE),
          applyRequiredPolicy(this.resourceId, this.resourceIdPolicy, Fields.RESOURCE_ID),
          applyRequiredPolicy(this.resourceType, this.resourceTypePolicy, Fields.RESOURCE_TYPE),
          applyRequiredPolicy(
              this.permissionTypes, this.permissionTypesPolicy, Fields.PERMISSION_TYPES));
    }
  }

  public interface OwnerIdStep {
    OwnerTypeStep ownerId(final String ownerId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OwnerTypeStep {
    ResourceIdStep ownerType(
        final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.OwnerTypeEnum> policy);
  }

  public interface ResourceIdStep {
    ResourceTypeStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(
        final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
        final ContractPolicy.FieldPolicy<io.camunda.gateway.protocol.model.ResourceTypeEnum>
            policy);
  }

  public interface PermissionTypesStep {
    OptionalStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes,
        final ContractPolicy.FieldPolicy<
                java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>>
            policy);
  }

  public interface OptionalStep {
    GeneratedAuthorizationIdBasedRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OWNER_ID =
        ContractPolicy.field("AuthorizationIdBasedRequest", "ownerId");
    public static final ContractPolicy.FieldRef OWNER_TYPE =
        ContractPolicy.field("AuthorizationIdBasedRequest", "ownerType");
    public static final ContractPolicy.FieldRef RESOURCE_ID =
        ContractPolicy.field("AuthorizationIdBasedRequest", "resourceId");
    public static final ContractPolicy.FieldRef RESOURCE_TYPE =
        ContractPolicy.field("AuthorizationIdBasedRequest", "resourceType");
    public static final ContractPolicy.FieldRef PERMISSION_TYPES =
        ContractPolicy.field("AuthorizationIdBasedRequest", "permissionTypes");

    private Fields() {}
  }
}
