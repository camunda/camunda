/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;

public enum BatchOperationItemSearchColumn implements SearchColumn<BatchOperationItemEntity> {
  BATCH_OPERATION_KEY("batchOperationKey"),
  ITEM_KEY("itemKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  STATE("state"),
  PROCESSED_DATE("processedDate");

  private final String property;

  BatchOperationItemSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<BatchOperationItemEntity> getEntityClass() {
    return BatchOperationItemEntity.class;
  }
}
