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

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeployedResourceEntity(
    Long resourceKey,
    String resourceId,
    String resourceName,
    Integer version,
    String versionTag,
    Long deploymentKey,
    String tenantId,
    String resourceContent)
    implements TenantOwnedEntity {

  public static class Builder implements ObjectBuilder<DeployedResourceEntity> {
    private Long resourceKey;
    private String resourceId;
    private String resourceName;
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
          version,
          versionTag,
          deploymentKey,
          tenantId,
          resourceContent);
    }
  }
}
