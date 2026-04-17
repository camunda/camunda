/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeployedResourceEntity(
    Long resourceKey,
    String resourceId,
    String resourceName,
    // null when fetched via the no-secondary-storage broker path (BrokerFetchResourceRequest does
    // not carry the type), or when ResourceUtils.deriveResourceType() cannot derive a type from the
    // resource name (no extension / empty name).
    @Nullable String resourceType,
    Integer version,
    @Nullable String versionTag,
    Long deploymentKey,
    String tenantId,
    @Nullable String resourceContent)
    implements TenantOwnedEntity {

  public DeployedResourceEntity {
    Objects.requireNonNull(resourceKey, "resourceKey");
    Objects.requireNonNull(resourceId, "resourceId");
    Objects.requireNonNull(resourceName, "resourceName");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(deploymentKey, "deploymentKey");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public static class Builder implements ObjectBuilder<DeployedResourceEntity> {
    private Long resourceKey;
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private Integer version;
    private String versionTag;
    private Long deploymentKey;
    private String tenantId;
    private String resourceContent;

    public Builder resourceKey(final Long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public Builder resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public Builder resourceType(final String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder version(final Integer version) {
      this.version = version;
      return this;
    }

    public Builder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public Builder deploymentKey(final Long deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder resourceContent(final String resourceContent) {
      this.resourceContent = resourceContent;
      return this;
    }

    @Override
    public DeployedResourceEntity build() {
      return new DeployedResourceEntity(
          resourceKey,
          resourceId,
          resourceName,
          resourceType,
          version,
          versionTag,
          deploymentKey,
          tenantId,
          resourceContent);
    }
  }
}
