/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record ResourceDbModel(
    Long resourceKey,
    String resourceId,
    String resourceName,
    int version,
    String versionTag,
    Long deploymentKey,
    String tenantId) {

  public static class ResourceDbModelBuilder implements ObjectBuilder<ResourceDbModel> {

    private Long resourceKey;
    private String resourceId;
    private String resourceName;
    private int version;
    private String versionTag;
    private Long deploymentKey;
    private String tenantId;

    public ResourceDbModelBuilder resourceKey(final Long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public ResourceDbModelBuilder resourceId(final String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ResourceDbModelBuilder resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public ResourceDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public ResourceDbModelBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public ResourceDbModelBuilder deploymentKey(final Long deploymentKey) {
      this.deploymentKey = deploymentKey;
      return this;
    }

    public ResourceDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ResourceDbModel build() {
      return new ResourceDbModel(
          resourceKey, resourceId, resourceName, version, versionTag, deploymentKey, tenantId);
    }
  }
}
