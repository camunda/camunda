/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion880;

public class TenantEntity extends AbstractExporterEntity<TenantEntity> {

  @SinceVersion880 private Long key;
  @SinceVersion880 private String tenantId;
  @SinceVersion880 private String name;
  @SinceVersion880 private String description;

  @SinceVersion880 private EntityJoinRelation join;

  public Long getKey() {
    return key;
  }

  public TenantEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TenantEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getName() {
    return name;
  }

  public TenantEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public TenantEntity setDescription(final String description) {
    this.description = description;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public TenantEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
