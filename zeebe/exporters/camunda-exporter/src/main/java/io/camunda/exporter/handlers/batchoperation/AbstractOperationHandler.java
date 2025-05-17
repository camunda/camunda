/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.webapps.schema.entities.operation.OperationEntity;

public class AbstractOperationHandler {

  protected static final String ID_PATTERN = "%s_%s";
  protected final String indexName;

  public AbstractOperationHandler(final String indexName) {
    this.indexName = indexName;
  }

  public Class<OperationEntity> getEntityType() {
    return OperationEntity.class;
  }

  public OperationEntity createNewEntity(final String id) {
    return new OperationEntity().setId(id);
  }

  public String getIndexName() {
    return indexName;
  }

  protected String generateId(final long batchOperationId, final long itemKey) {
    return String.format(ID_PATTERN, batchOperationId, itemKey);
  }
}
