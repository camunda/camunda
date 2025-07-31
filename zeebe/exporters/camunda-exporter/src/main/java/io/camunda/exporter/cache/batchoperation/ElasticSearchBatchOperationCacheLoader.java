/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.batchoperation;

import static io.camunda.exporter.utils.ExporterUtil.map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.search.SourceConfigBuilders;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchBatchOperationCacheLoader
    implements CacheLoader<String, CachedBatchOperationEntity> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticSearchBatchOperationCacheLoader.class);

  private final ElasticsearchClient client;
  private final String batchOperationIndexName;

  public ElasticSearchBatchOperationCacheLoader(
      final ElasticsearchClient client, final String batchOperationIndexName) {
    this.client = client;
    this.batchOperationIndexName = batchOperationIndexName;
  }

  @Override
  public CachedBatchOperationEntity load(final String batchOperationKey) throws IOException {
    final var termQuery = QueryBuilders.ids(i -> i.values(batchOperationKey));
    final var sourceFilter =
        SourceConfigBuilders.filter()
            .includes(BatchOperationTemplate.ID, BatchOperationTemplate.TYPE)
            .build();
    final var response =
        client.search(
            request ->
                request
                    .index(batchOperationIndexName)
                    .query(termQuery)
                    .source(s -> s.filter(sourceFilter))
                    .size(1),
            BatchOperationEntity.class);
    if (response.hits() != null && !response.hits().hits().isEmpty()) {
      final var entity = response.hits().hits().getFirst().source();
      return new CachedBatchOperationEntity(entity.getId(), map(entity.getType()));
    } else {
      LOG.debug("BatchOperation '{}' not found in Elasticsearch", batchOperationKey);
      return null;
    }
  }
}
