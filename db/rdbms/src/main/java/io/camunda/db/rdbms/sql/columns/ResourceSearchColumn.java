/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ResourceEntity;

public enum ResourceSearchColumn implements SearchColumn<ResourceEntity> {
  RESOURCE_KEY("resourceKey"),
  RESOURCE_ID("resourceId"),
  RESOURCE_NAME("resourceName"),
  VERSION("version"),
  VERSION_TAG("versionTag"),
  DEPLOYMENT_KEY("deploymentKey"),
  TENANT_ID("tenantId");

  private final String property;

  ResourceSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ResourceEntity> getEntityClass() {
    return ResourceEntity.class;
  }
}
