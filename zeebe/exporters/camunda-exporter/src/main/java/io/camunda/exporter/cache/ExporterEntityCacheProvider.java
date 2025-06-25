/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;

public interface ExporterEntityCacheProvider {

  CacheLoader<String, CachedBatchOperationEntity> getBatchOperationCacheLoader(
      String batchOperationIndexName);

  CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(String processIndexName);

  CacheLoader<String, CachedFormEntity> getFormCacheLoader(String formIndexName);
}
