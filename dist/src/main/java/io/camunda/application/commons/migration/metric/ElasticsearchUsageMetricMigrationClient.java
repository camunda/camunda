/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration.metric;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ElasticsearchUsageMetricMigrationClient(
    ElasticsearchClient client, ElasticsearchTransformers transformers)
    implements UsageMetricMigrationClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchUsageMetricMigrationClient.class);

  private SearchTransfomer<SearchQuery, Query> getSearchQueryTransformer() {
    return transformers.getTransformer(SearchQuery.class);
  }

  @Override
  public UsageMetricsEntity getFirstUsageMetricEntity(
      final String index, final SearchQuery searchQuery) throws IOException {

    final SearchResponse<UsageMetricsEntity> response =
        client.search(
            b ->
                b.index(index)
                    .from(0)
                    .size(1)
                    .query(getSearchQueryTransformer().apply(searchQuery))
                    .sort(
                        s ->
                            s.field(
                                f -> f.field(UsageMetricIndex.EVENT_TIME).order(SortOrder.Asc))),
            UsageMetricsEntity.class);

    final var hits = response.hits().hits();
    if (!hits.isEmpty()) {
      return hits.getFirst().source();
    }
    return null;
  }

  @Override
  public ReindexResult reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script) {

    try {
      if (!client.indices().exists(e -> e.index(src, dest)).value()) {
        LOG.warn("Source or destination index [{}, {}] does not exist", src, dest);
        return new ReindexResult(false, src, dest, null);
      }

      LOG.info("Reindexing [{}] into [{}]...", src, dest);
      final var reindexRequest =
          new ReindexRequest.Builder()
              .conflicts(Conflicts.Proceed)
              .source(s -> s.index(src).query(getSearchQueryTransformer().apply(searchQuery)))
              .dest(d -> d.index(dest).opType(OpType.Create))
              .script(s -> s.source(script).lang(LANG_PAINLESS))
              .build();
      final var response = client.reindex(reindexRequest);

      if (!response.failures().isEmpty()) {
        LOG.error(
            "Failed to reindex [{}]: {}",
            src,
            response.failures().stream().map(f -> f.cause().reason()).toList());
        return new ReindexResult(false, src, dest, null);
      } else if (response.versionConflicts() != null && response.versionConflicts() > 0) {
        LOG.warn(
            "Reindexing [{}] into [{}] completed with version conflicts: {}",
            src,
            dest,
            response.versionConflicts());
      }

      LOG.info("Successfully reindexed [{}] with {} documents", src, response.created());
      return new ReindexResult(true, src, dest, null);

    } catch (final IOException e) {
      LOG.error("Failed to reindex [{}] into [{}]", src, dest, e);
      return new ReindexResult(false, src, dest, e.getMessage());
    }
  }

  @Override
  public UsageMetricsEntity getLatestMigratedEntity(
      final String index, final SearchQuery searchQuery) throws IOException {

    final SearchResponse<UsageMetricsEntity> response =
        client.search(
            b ->
                b.index(index)
                    .from(0)
                    .size(1)
                    .query(getSearchQueryTransformer().apply(searchQuery))
                    .sort(
                        s ->
                            s.field(
                                f -> f.field(UsageMetricIndex.EVENT_TIME).order(SortOrder.Asc))),
            UsageMetricsEntity.class);

    final var hits = response.hits().hits();
    if (!hits.isEmpty()) {
      return hits.getFirst().source();
    }
    return null;
  }
}
