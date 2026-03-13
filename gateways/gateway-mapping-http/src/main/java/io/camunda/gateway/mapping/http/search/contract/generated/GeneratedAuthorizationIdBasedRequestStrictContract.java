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
    private io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType;
    private String resourceId;
    private io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType;
    private java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(final String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    @Override
    public ResourceIdStep ownerType(
        final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    @Override
    public ResourceTypeStep resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public PermissionTypesStep resourceType(
        final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    @Override
    public OptionalStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum>
            permissionTypes) {
      this.permissionTypes = permissionTypes;
      return this;
    }

    @Override
    public GeneratedAuthorizationIdBasedRequestStrictContract build() {
      return new GeneratedAuthorizationIdBasedRequestStrictContract(
          this.ownerId, this.ownerType, this.resourceId, this.resourceType, this.permissionTypes);
    }
  }

  public interface OwnerIdStep {
    OwnerTypeStep ownerId(final String ownerId);
  }

  public interface OwnerTypeStep {
    ResourceIdStep ownerType(final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType);
  }

  public interface ResourceIdStep {
    ResourceTypeStep resourceId(final String resourceId);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(
        final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType);
  }

  public interface PermissionTypesStep {
    OptionalStep permissionTypes(
        final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes);
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
