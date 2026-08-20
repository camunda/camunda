/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.BatchOperationEntity;

public enum BatchOperationSearchColumn implements SearchColumn<BatchOperationEntity> {
  BATCH_OPERATION_KEY("batchOperationKey"),
  STATE("state"),
  OPERATION_TYPE("operationType"),
  START_DATE("startDate"),
  END_DATE("endDate"),
  ACTOR_TYPE("actorType"),
  ACTOR_ID("actorId");

  private final String property;

  BatchOperationSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<BatchOperationEntity> getEntityClass() {
    return BatchOperationEntity.class;
  }
}
