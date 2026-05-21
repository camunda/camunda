/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.resource;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.util.Objects;

public final class DeployedResourceEntity implements ExporterEntity<DeployedResourceEntity> {

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long resourceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String resourceId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String resourceName;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String resourceType;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int version;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String versionTag;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long deploymentKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String resourceContent;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DeployedResourceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getResourceKey() {
    return resourceKey;
  }

  public DeployedResourceEntity setResourceKey(final long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public DeployedResourceEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public DeployedResourceEntity setResourceName(final String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public String getResourceType() {
    return resourceType;
  }

  public DeployedResourceEntity setResourceType(final String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public DeployedResourceEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public DeployedResourceEntity setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DeployedResourceEntity setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DeployedResourceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getResourceContent() {
    return resourceContent;
  }

  public DeployedResourceEntity setResourceContent(final String resourceContent) {
    this.resourceContent = resourceContent;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
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

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeployedResourceEntity that = (DeployedResourceEntity) o;
    return resourceKey == that.resourceKey
        && version == that.version
        && deploymentKey == that.deploymentKey
        && Objects.equals(id, that.id)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(resourceType, that.resourceType)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(resourceContent, that.resourceContent);
  }

  @Override
  public String toString() {
    return "DeployedResourceEntity["
        + "id='"
        + id
        + '\''
        + ", resourceKey="
        + resourceKey
        + ", resourceId='"
        + resourceId
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", resourceType='"
        + resourceType
        + '\''
        + ", version="
        + version
        + ", versionTag='"
        + versionTag
        + '\''
        + ", deploymentKey="
        + deploymentKey
        + ", tenantId='"
        + tenantId
        + '\''
        + ']';
  }
}
