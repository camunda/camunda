/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record DeployedResourceDbModel(
    Long resourceKey,
    String resourceId,
    String resourceName,
    int version,
    String versionTag,
    Long deploymentKey,
    String tenantId,
    String resourceContent) {

  public static class DeployedResourceDbModelBuilder
      implements ObjectBuilder<DeployedResourceDbModel> {

    private Long resourceKey;
    private String resourceId;
    private String resourceName;
    private int version;
    private String versionTag;
    private Long deploymentKey;
    private String tenantId;
    private String resourceContent;

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

    public DeployedResourceDbModelBuilder resourceContent(final String resourceContent) {
      this.resourceContent = resourceContent;
      return this;
    }

    @Override
    public DeployedResourceDbModel build() {
      return new DeployedResourceDbModel(
          resourceKey,
          resourceId,
          resourceName,
          version,
          versionTag,
          deploymentKey,
          tenantId,
          resourceContent);
    }
  }
}
