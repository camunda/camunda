/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.exporter.rdbms.utils.ProcessCacheUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsProcessCacheLoader implements CacheLoader<Long, CachedProcessEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsProcessCacheLoader.class);
  private final ProcessDefinitionReader reader;

  public RdbmsProcessCacheLoader(final ProcessDefinitionReader reader) {
    this.reader = reader;
  }

  @Override
  public CachedProcessEntity load(final @NotNull Long key) throws Exception {
    final var response = reader.findOne(key);
    if (response.isPresent()) {
      final var processDefinitionEntity = response.get();
      return new CachedProcessEntity(
          processDefinitionEntity.name(),
          processDefinitionEntity.versionTag(),
          ProcessCacheUtil.extractCallActivityIdsFromDiagram(processDefinitionEntity));
    }
    LOG.debug("Process '{}' not found in RDBMS", key);
    return null;
  }
}
