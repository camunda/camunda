/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.TenantEntity;

public enum TenantSearchColumn implements SearchColumn<TenantEntity> {
  TENANT_KEY("key"),
  TENANT_ID("tenantId"),
  NAME("name");

  private final String property;

  TenantSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<TenantEntity> getEntityClass() {
    return TenantEntity.class;
  }
}
