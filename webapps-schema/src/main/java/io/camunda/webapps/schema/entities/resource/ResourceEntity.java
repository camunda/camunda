/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.resource;

import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.Arrays;
import java.util.Objects;

public final class ResourceEntity implements ExporterEntity<ResourceEntity> {

  private String id;
  private long resourceKey;
  private String resourceId;
  private String resourceName;
  private int version;
  private String versionTag;
  private long deploymentKey;
  private String tenantId;
  private byte[] checksum;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ResourceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getResourceKey() {
    return resourceKey;
  }

  public ResourceEntity setResourceKey(final long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public ResourceEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public ResourceEntity setResourceName(final String resourceName) {
    this.resourceName = resourceName;
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

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ResourceEntity setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ResourceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public byte[] getChecksum() {
    return checksum;
  }

  public ResourceEntity setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id, resourceKey, resourceId, resourceName, version, versionTag, deploymentKey,
            tenantId);
    result = 31 * result + Arrays.hashCode(checksum);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResourceEntity that = (ResourceEntity) o;
    return resourceKey == that.resourceKey
        && version == that.version
        && deploymentKey == that.deploymentKey
        && Objects.equals(id, that.id)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(checksum, that.checksum);
  }

  @Override
  public String toString() {
    return "ResourceEntity["
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
