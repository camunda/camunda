/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.Arrays;
import java.util.Objects;

public record DeployedResourceDbModel(
    Long resourceKey,
    String resourceId,
    String resourceName,
    String resourceType,
    int version,
    String versionTag,
    Long deploymentKey,
    String tenantId,
    byte[] resourceContent) {

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final DeployedResourceDbModel that)) {
      return false;
    }
    return Objects.equals(resourceKey, that.resourceKey)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(resourceType, that.resourceType)
        && version == that.version
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(deploymentKey, that.deploymentKey)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(resourceContent, that.resourceContent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        resourceKey,
        resourceId,
        resourceName,
        resourceType,
        version,
        versionTag,
        deploymentKey,
        tenantId,
        Arrays.hashCode(resourceContent));
  }

  public static class DeployedResourceDbModelBuilder
      implements ObjectBuilder<DeployedResourceDbModel> {

    private Long resourceKey;
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private int version;
    private String versionTag;
    private Long deploymentKey;
    private String tenantId;
    private byte[] resourceContent;

    public DeployedResourceDbModelBuilder resourceKey(final Long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public DeployedResourceDbModelBuilder resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public DeployedResourceDbModelBuilder resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public DeployedResourceDbModelBuilder resourceType(final String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public DeployedResourceDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public DeployedResourceDbModelBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public DeployedResourceDbModelBuilder deploymentKey(final Long deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public DeployedResourceDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public DeployedResourceDbModelBuilder resourceContent(final byte[] resourceContent) {
      this.resourceContent = resourceContent;
      return this;
    }

    @Override
    public DeployedResourceDbModel build() {
      return new DeployedResourceDbModel(
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
