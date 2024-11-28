/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public class TenantEntity extends AbstractExporterEntity<TenantEntity> {

  private Long tenantKey;
  private String tenantId;
  private String name;
  private Long memberKey;

  private EntityJoinRelation join;

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

  public Long getMemberKey() {
    return memberKey;
  }

  public TenantEntity setMemberKey(final Long memberKey) {
    this.memberKey = memberKey;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public TenantEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }

  public static String getChildKey(final long tenantKey, final long memberKey) {
    return String.format("%d-%d", tenantKey, memberKey);
  }
}
