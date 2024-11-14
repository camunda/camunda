/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchProcessCacheLoader implements CacheLoader<Long, CachedProcessEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchProcessCacheLoader.class);

  private final String processIndexName;
  private final Function<Long, ProcessEntity> processEntitySupplier;

  public ElasticSearchProcessCacheLoader(
      final Function<Long, ProcessEntity> processEntitySupplier, final String processIndexName) {
    this.processEntitySupplier = processEntitySupplier;
    this.processIndexName = processIndexName;
  }

  @Override
  public CachedProcessEntity load(final Long processDefinitionKey) throws IOException {
    final var processEntity = processEntitySupplier.apply(processDefinitionKey);
    if (processEntity == null) {
      // This should only happen if the process was deleted from ElasticSearch which should never
      // happen. Normally, the process is exported before the process instance is exporter. So the
      // process should be found in ElasticSearch index.
      LOG.debug("Process '{}' not found in Elasticsearch", processDefinitionKey);
      return null;
    }

    return new CachedProcessEntity(processEntity.getName(), processEntity.getVersionTag());
  }
}
