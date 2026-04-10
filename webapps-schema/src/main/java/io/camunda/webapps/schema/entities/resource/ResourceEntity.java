/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.resource;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class ResourceEntity implements ExporterEntity<ResourceEntity>, TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private String resourceId;
  @BeforeVersion880 private int version;
  @BeforeVersion880 private String versionTag;
  @BeforeVersion880 private String resourceName;
  @BeforeVersion880 private String resource;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  @BeforeVersion880 private long deploymentKey;
  @BeforeVersion880 private Boolean isDeleted;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ResourceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ResourceEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public ResourceEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public ResourceEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ResourceEntity setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public ResourceEntity setResourceName(final String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public String getResource() {
    return resource;
  }

  public ResourceEntity setResource(final String resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ResourceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ResourceEntity setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public ResourceEntity setIsDeleted(final Boolean deleted) {
    isDeleted = deleted;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        resourceId,
        version,
        versionTag,
        resourceName,
        resource,
        tenantId,
        deploymentKey,
        isDeleted);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResourceEntity that = (ResourceEntity) o;
    return key == that.key
        && version == that.version
        && deploymentKey == that.deploymentKey
        && Objects.equals(id, that.id)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(resource, that.resource)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(isDeleted, that.isDeleted);
  }

  @Override
  public String toString() {
    return "ResourceEntity{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", resourceId='"
        + resourceId
        + '\''
        + ", version="
        + version
        + ", versionTag='"
        + versionTag
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", deploymentKey="
        + deploymentKey
        + ", isDeleted="
        + isDeleted
        + '}';
  }
}
