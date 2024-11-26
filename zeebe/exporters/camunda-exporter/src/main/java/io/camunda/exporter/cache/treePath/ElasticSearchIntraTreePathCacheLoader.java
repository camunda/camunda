/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.treePath;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchIntraTreePathCacheLoader
    implements CacheLoader<CachedTreePathKey, String> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticSearchIntraTreePathCacheLoader.class);

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticSearchIntraTreePathCacheLoader(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public String load(final CachedTreePathKey cachedTreePathKey) throws IOException {
    final var response =
        client.get(
            request ->
                request
                    .index(indexName)
                    .id(String.valueOf(cachedTreePathKey.flowNodeInstanceKey())),
            FlowNodeInstanceEntity.class);
    if (response.found() && response.source() != null) {
      final var flowNodeInstanceEntity = response.source();
      return flowNodeInstanceEntity.getTreePath();
    } else {
      // This should only happen if the process was deleted from ElasticSearch which should
      //     never
      // happen. Normally, the process is exported before the process instance is exporter. So
      //     the
      // process should be found in ElasticSearch index.
      LOG.debug("FNI '{}' not found in Elasticsearch", cachedTreePathKey.flowNodeInstanceKey());
      return null;
    }
  }
}
