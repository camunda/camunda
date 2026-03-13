/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/deployments.yaml#/components/schemas/ResourceResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedResourceStrictContract(
    String resourceName,
    Integer version,
    @Nullable String versionTag,
    String resourceId,
    String tenantId,
    String resourceKey
) {

  public GeneratedResourceStrictContract {
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(resourceId, "resourceId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(resourceKey, "resourceKey is required and must not be null");
  }


  public static ResourceNameStep builder() {
    return new Builder();
  }

  public static final class Builder implements ResourceNameStep, VersionStep, ResourceIdStep, TenantIdStep, ResourceKeyStep, OptionalStep {
    private String resourceName;
    private Integer version;
    private String versionTag;
    private String resourceId;
    private String tenantId;
    private String resourceKey;

    private Builder() {}

    @Override
    public VersionStep resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public ResourceIdStep version(final Integer version) {
      this.version = version;
      return this;
    }

    @Override
    public TenantIdStep resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public ResourceKeyStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep resourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    @Override
    public OptionalStep versionTag(final @Nullable String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    @Override
    public OptionalStep versionTag(final @Nullable String versionTag, final ContractPolicy.FieldPolicy<String> policy) {
      this.versionTag = policy.apply(versionTag, Fields.VERSION_TAG, null);
      return this;
    }

    @Override
    public GeneratedResourceStrictContract build() {
      return new GeneratedResourceStrictContract(
          this.resourceName,
          this.version,
          this.versionTag,
          this.resourceId,
          this.tenantId,
          this.resourceKey);
    }
  }

  public interface ResourceNameStep {
    VersionStep resourceName(final String resourceName);
  }

  public interface VersionStep {
    ResourceIdStep version(final Integer version);
  }

  public interface ResourceIdStep {
    TenantIdStep resourceId(final String resourceId);
  }

  public interface TenantIdStep {
    ResourceKeyStep tenantId(final String tenantId);
  }

  public interface ResourceKeyStep {
    OptionalStep resourceKey(final String resourceKey);
  }

  public interface OptionalStep {
  OptionalStep versionTag(final @Nullable String versionTag);

  OptionalStep versionTag(final @Nullable String versionTag, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedResourceStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef RESOURCE_NAME = ContractPolicy.field("ResourceResult", "resourceName");
    public static final ContractPolicy.FieldRef VERSION = ContractPolicy.field("ResourceResult", "version");
    public static final ContractPolicy.FieldRef VERSION_TAG = ContractPolicy.field("ResourceResult", "versionTag");
    public static final ContractPolicy.FieldRef RESOURCE_ID = ContractPolicy.field("ResourceResult", "resourceId");
    public static final ContractPolicy.FieldRef TENANT_ID = ContractPolicy.field("ResourceResult", "tenantId");
    public static final ContractPolicy.FieldRef RESOURCE_KEY = ContractPolicy.field("ResourceResult", "resourceKey");

    private Fields() {}
  }


}
