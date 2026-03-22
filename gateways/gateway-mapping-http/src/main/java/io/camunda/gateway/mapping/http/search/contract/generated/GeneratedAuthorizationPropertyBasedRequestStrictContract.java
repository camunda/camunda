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
public record GeneratedAuthorizationPropertyBasedRequestStrictContract(
    @JsonProperty("ownerId") String ownerId,
    @JsonProperty("ownerType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum ownerType,
    @JsonProperty("resourcePropertyName") String resourcePropertyName,
    @JsonProperty("resourceType")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceTypeEnum
            resourceType,
    @JsonProperty("permissionTypes")
        java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedPermissionTypeEnum>
            permissionTypes)
    implements GeneratedAuthorizationRequestStrictContract {

  public GeneratedAuthorizationPropertyBasedRequestStrictContract {
    Objects.requireNonNull(ownerId, "No ownerId provided.");
    Objects.requireNonNull(ownerType, "No ownerType provided.");
    Objects.requireNonNull(resourcePropertyName, "No resourcePropertyName provided.");
    Objects.requireNonNull(resourceType, "No resourceType provided.");
    Objects.requireNonNull(permissionTypes, "No permissionTypes provided.");
  }

  public static OwnerIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements OwnerIdStep,
          OwnerTypeStep,
          ResourcePropertyNameStep,
          ResourceTypeStep,
          PermissionTypesStep,
          OptionalStep {
    private String ownerId;
    private io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
        ownerType;
    private String resourcePropertyName;
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
    public ResourcePropertyNameStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType) {
      this.ownerType = ownerType;
      return this;
    }

    @Override
    public ResourceTypeStep resourcePropertyName(final String resourcePropertyName) {
      this.resourcePropertyName = resourcePropertyName;
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
    ResourcePropertyNameStep ownerType(
        final io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOwnerTypeEnum
            ownerType);
  }

  public interface ResourcePropertyNameStep {
    ResourceTypeStep resourcePropertyName(final String resourcePropertyName);
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
    GeneratedAuthorizationPropertyBasedRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef OWNER_ID =
        ContractPolicy.field("AuthorizationPropertyBasedRequest", "ownerId");
    public static final ContractPolicy.FieldRef OWNER_TYPE =
        ContractPolicy.field("AuthorizationPropertyBasedRequest", "ownerType");
    public static final ContractPolicy.FieldRef RESOURCE_PROPERTY_NAME =
        ContractPolicy.field("AuthorizationPropertyBasedRequest", "resourcePropertyName");
    public static final ContractPolicy.FieldRef RESOURCE_TYPE =
        ContractPolicy.field("AuthorizationPropertyBasedRequest", "resourceType");
    public static final ContractPolicy.FieldRef PERMISSION_TYPES =
        ContractPolicy.field("AuthorizationPropertyBasedRequest", "permissionTypes");

    private Fields() {}
  }
}
