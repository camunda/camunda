/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsProcessCacheLoader implements CacheLoader<Long, CachedProcessEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsProcessCacheLoader.class);
  private final ProcessDefinitionDbReader reader;

  public RdbmsProcessCacheLoader(final ProcessDefinitionDbReader reader) {
    this.reader = reader;
  }

  @Override
  public CachedProcessEntity load(final @NotNull Long key) throws Exception {
    final var response = reader.findOne(key);
    if (response.isPresent()) {
      final var processDefinitionEntity = response.get();
      final var processDiagramData =
          ProcessCacheUtil.extractProcessDiagramData(processDefinitionEntity);
      return new CachedProcessEntity(
          processDefinitionEntity.name(),
          processDefinitionEntity.version(),
          processDefinitionEntity.versionTag(),
          processDiagramData.callActivityIds(),
          processDiagramData.flowNodesMap());
    }
    LOG.debug("Process '{}' not found in RDBMS", key);
    return null;
  }

  @Override
  public @NotNull Map<Long, CachedProcessEntity> loadAll(final @NotNull Set<? extends Long> keys) {
    final var query =
        ProcessDefinitionQuery.of(b -> b.filter(f -> f.processDefinitionKeys(List.copyOf(keys))));
    final var response = reader.search(query);
    return response.items().stream()
        .collect(
            Collectors.toMap(
                ProcessDefinitionEntity::processDefinitionKey,
                pde -> {
                  final var processDiagramData = ProcessCacheUtil.extractProcessDiagramData(pde);
                  return new CachedProcessEntity(
                      pde.name(),
                      pde.version(),
                      pde.versionTag(),
                      processDiagramData.callActivityIds(),
                      processDiagramData.flowNodesMap());
                }));
  }
}
