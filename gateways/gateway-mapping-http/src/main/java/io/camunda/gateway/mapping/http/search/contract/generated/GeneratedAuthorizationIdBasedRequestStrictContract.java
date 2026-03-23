/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/authorizations.yaml#/components/schemas/AuthorizationIdBasedRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationIdBasedRequestStrictContract(
    @JsonProperty("ownerId") String ownerId,
    @JsonProperty("ownerType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum ownerType,
    @JsonProperty("resourceId") String resourceId,
    @JsonProperty("resourceType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType,
    @JsonProperty("permissionTypes")
        java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes)
    implements GeneratedAuthorizationRequestStrictContract {

  public GeneratedAuthorizationIdBasedRequestStrictContract {
    Objects.requireNonNull(ownerId, "No ownerId provided.");
    Objects.requireNonNull(ownerType, "No ownerType provided.");
    Objects.requireNonNull(resourceId, "No resourceId provided.");
    Objects.requireNonNull(resourceType, "No resourceType provided.");
    Objects.requireNonNull(permissionTypes, "No permissionTypes provided.");
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
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
        ownerType;
    private String resourceId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
        resourceType;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated.GeneratedPermissionTypeEnum>
        permissionTypes;

    private Builder() {}

    @Override
    public OwnerTypeStep ownerId(final String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    @Override
    public ResourceIdStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType) {
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
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    @Override
    public OptionalStep permissionTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
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
    ResourceIdStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType);
  }

  public interface ResourceIdStep {
    ResourceTypeStep resourceId(final String resourceId);
  }

  public interface ResourceTypeStep {
    PermissionTypesStep resourceType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType);
  }

  public interface PermissionTypesStep {
    OptionalStep permissionTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes);
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
