/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.rdbms.read.service.BatchOperationReader;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsBatchOperationCacheLoader
    implements CacheLoader<String, CachedBatchOperationEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsBatchOperationCacheLoader.class);
  private final BatchOperationReader reader;

  public RdbmsBatchOperationCacheLoader(final BatchOperationReader reader) {
    this.reader = reader;
  }

  @Override
  public CachedBatchOperationEntity load(final @NotNull String key) throws Exception {
    final var response = reader.findOneDbModel(key);
    if (response.isPresent()) {
      final var batchOperation = response.get();
      return new CachedBatchOperationEntity(
          batchOperation.batchOperationId(),
          OperationType.valueOf(batchOperation.operationType()),
          batchOperation.exportItemsOnCreation());
    }
    LOG.debug("BatchOperation '{}' not found in RDBMS", key);
    return null;
  }
}
