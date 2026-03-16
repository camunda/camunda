/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/authorizations.yaml#/components/schemas/AuthorizationPropertyBasedRequest
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
public record GeneratedAuthorizationPropertyBasedRequestStrictContract(
    String ownerId,
    io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType,
    String resourcePropertyName,
    io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType,
    java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes
) {

  public GeneratedAuthorizationPropertyBasedRequestStrictContract {
    Objects.requireNonNull(ownerId, "ownerId is required and must not be null");
    Objects.requireNonNull(ownerType, "ownerType is required and must not be null");
    Objects.requireNonNull(resourcePropertyName, "resourcePropertyName is required and must not be null");
    Objects.requireNonNull(resourceType, "resourceType is required and must not be null");
    Objects.requireNonNull(permissionTypes, "permissionTypes is required and must not be null");
  }


  public static OwnerIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements OwnerIdStep, OwnerTypeStep, ResourcePropertyNameStep, ResourceTypeStep, PermissionTypesStep, OptionalStep {
    private String ownerId;
    private io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType;
    private String resourcePropertyName;
    private io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType;
    private java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(final String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    @Override
    public ResourcePropertyNameStep ownerType(final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    @Override
    public ResourceTypeStep resourcePropertyName(final String resourcePropertyName) {
      this.resourcePropertyName = resourcePropertyName;
      return this;
    }

    @Override
    public PermissionTypesStep resourceType(final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    @Override
    public OptionalStep permissionTypes(final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes) {
      this.permissionTypes = permissionTypes;
      return this;
    }
    @Override
    public GeneratedAuthorizationPropertyBasedRequestStrictContract build() {
      return new GeneratedAuthorizationPropertyBasedRequestStrictContract(
          this.ownerId,
          this.ownerType,
          this.resourcePropertyName,
          this.resourceType,
          this.permissionTypes);
    }
  }

  public interface OwnerIdStep {
    OwnerTypeStep ownerId(final String ownerId);
  }

  public interface OwnerTypeStep {
    ResourcePropertyNameStep ownerType(final io.camunda.gateway.protocol.model.OwnerTypeEnum ownerType);
  }

  public interface ResourcePropertyNameStep {
    ResourceTypeStep resourcePropertyName(final String resourcePropertyName);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(final io.camunda.gateway.protocol.model.ResourceTypeEnum resourceType);
  }

  public interface PermissionTypesStep {
    OptionalStep permissionTypes(final java.util.List<io.camunda.gateway.protocol.model.PermissionTypeEnum> permissionTypes);
  }

  public interface OptionalStep {
    GeneratedAuthorizationPropertyBasedRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef OWNER_ID = ContractPolicy.field("AuthorizationPropertyBasedRequest", "ownerId");
    public static final ContractPolicy.FieldRef OWNER_TYPE = ContractPolicy.field("AuthorizationPropertyBasedRequest", "ownerType");
    public static final ContractPolicy.FieldRef RESOURCE_PROPERTY_NAME = ContractPolicy.field("AuthorizationPropertyBasedRequest", "resourcePropertyName");
    public static final ContractPolicy.FieldRef RESOURCE_TYPE = ContractPolicy.field("AuthorizationPropertyBasedRequest", "resourceType");
    public static final ContractPolicy.FieldRef PERMISSION_TYPES = ContractPolicy.field("AuthorizationPropertyBasedRequest", "permissionTypes");

    private Fields() {}
  }


}
