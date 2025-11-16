/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ClusterVariableEntity;

public enum ClusterVariableSearchColumn implements SearchColumn<ClusterVariableEntity> {
  CLUSTER_VARIABLE_ID("id"),
  VAR_NAME("name"),
  VAR_VALUE("value"),
  VAR_FULL_VALUE("fullValue"),
  TENANT_ID("tenantId"),
  SCOPE("scope"),
  IS_PREVIEW("isPreview");
  private final String property;

  ClusterVariableSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ClusterVariableEntity> getEntityClass() {
    return ClusterVariableEntity.class;
  }
}
