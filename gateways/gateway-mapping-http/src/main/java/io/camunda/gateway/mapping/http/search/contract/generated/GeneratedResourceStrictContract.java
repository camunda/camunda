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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedResourceStrictContract(
    String resourceName,
    Integer version,
    @Nullable String versionTag,
    String resourceId,
    String tenantId,
    String resourceKey) {

  public GeneratedResourceStrictContract {
    Objects.requireNonNull(resourceName, "resourceName is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
    Objects.requireNonNull(resourceId, "resourceId is required and must not be null");
    Objects.requireNonNull(tenantId, "tenantId is required and must not be null");
    Objects.requireNonNull(resourceKey, "resourceKey is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ResourceNameStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ResourceNameStep,
          VersionStep,
          ResourceIdStep,
          TenantIdStep,
          ResourceKeyStep,
          OptionalStep {
    private String resourceName;
    private ContractPolicy.FieldPolicy<String> resourceNamePolicy;
    private Integer version;
    private ContractPolicy.FieldPolicy<Integer> versionPolicy;
    private String versionTag;
    private String resourceId;
    private ContractPolicy.FieldPolicy<String> resourceIdPolicy;
    private String tenantId;
    private ContractPolicy.FieldPolicy<String> tenantIdPolicy;
    private String resourceKey;
    private ContractPolicy.FieldPolicy<String> resourceKeyPolicy;

    private Builder() {}

    @Override
    public VersionStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceName = resourceName;
      this.resourceNamePolicy = policy;
      return this;
    }

    @Override
    public ResourceIdStep version(
        final Integer version, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public TenantIdStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceId = resourceId;
      this.resourceIdPolicy = policy;
      return this;
    }

    @Override
    public ResourceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = tenantId;
      this.tenantIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep resourceKey(
        final String resourceKey, final ContractPolicy.FieldPolicy<String> policy) {
      this.resourceKey = resourceKey;
      this.resourceKeyPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    @Override
    public OptionalStep versionTag(
        final String versionTag, final ContractPolicy.FieldPolicy<String> policy) {
      this.versionTag = policy.apply(versionTag, Fields.VERSION_TAG, null);
      return this;
    }

    @Override
    public GeneratedResourceStrictContract build() {
      return new GeneratedResourceStrictContract(
          applyRequiredPolicy(this.resourceName, this.resourceNamePolicy, Fields.RESOURCE_NAME),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION),
          this.versionTag,
          applyRequiredPolicy(this.resourceId, this.resourceIdPolicy, Fields.RESOURCE_ID),
          applyRequiredPolicy(this.tenantId, this.tenantIdPolicy, Fields.TENANT_ID),
          applyRequiredPolicy(this.resourceKey, this.resourceKeyPolicy, Fields.RESOURCE_KEY));
    }
  }

  public interface ResourceNameStep {
    VersionStep resourceName(
        final String resourceName, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface VersionStep {
    ResourceIdStep version(final Integer version, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ResourceIdStep {
    TenantIdStep resourceId(
        final String resourceId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface TenantIdStep {
    ResourceKeyStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResourceKeyStep {
    OptionalStep resourceKey(
        final String resourceKey, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep versionTag(final String versionTag);

    OptionalStep versionTag(
        final String versionTag, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedResourceStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef RESOURCE_NAME =
        ContractPolicy.field("ResourceResult", "resourceName");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("ResourceResult", "version");
    public static final ContractPolicy.FieldRef VERSION_TAG =
        ContractPolicy.field("ResourceResult", "versionTag");
    public static final ContractPolicy.FieldRef RESOURCE_ID =
        ContractPolicy.field("ResourceResult", "resourceId");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ResourceResult", "tenantId");
    public static final ContractPolicy.FieldRef RESOURCE_KEY =
        ContractPolicy.field("ResourceResult", "resourceKey");

    private Fields() {}
  }
}
