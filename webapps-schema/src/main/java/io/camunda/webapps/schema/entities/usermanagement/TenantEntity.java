/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.util.Set;

public class TenantEntity extends AbstractExporterEntity<TenantEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";

  private String id;
  private Long tenantKey;
  private String tenantId;
  private String name;
  private Set<Long> assignedMemberKeys;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public TenantEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getTenantKey() {
    return tenantKey;
  }

  public TenantEntity setTenantKey(final Long tenantKey) {
    this.tenantKey = tenantKey;
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

  public Set<Long> getAssignedMemberKeys() {
    return assignedMemberKeys;
  }

  public void setAssignedMemberKeys(final Set<Long> assignedMemberKeys) {
    this.assignedMemberKeys = assignedMemberKeys;
  }
}
