/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;

public enum IncidentProcessInstanceStatisticsByErrorSearchColumn
    implements SearchColumn<IncidentProcessInstanceStatisticsByErrorEntity> {
  ERROR_HASH_CODE("errorHashCode"),
  ERROR_MESSAGE("errorMessage"),
  ACTIVE_INSTANCES_WITH_ERROR_COUNT("activeInstancesWithErrorCount");

  private final String property;

  IncidentProcessInstanceStatisticsByErrorSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<IncidentProcessInstanceStatisticsByErrorEntity> getEntityClass() {
    return IncidentProcessInstanceStatisticsByErrorEntity.class;
  }
}
