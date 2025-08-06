/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexRequest.Builder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.usagemetric.client.MigrationStep;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import java.io.IOException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchUsageMetricMigrationClient implements UsageMetricMigrationClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchUsageMetricMigrationClient.class);
  private final ElasticsearchClient client;
  private final ElasticsearchTransformers transformers;

  public ElasticsearchUsageMetricMigrationClient(final ConnectConfiguration connect) {
    client = new ElasticsearchConnector(connect).createClient();
    transformers = new ElasticsearchTransformers();
  }

  private SearchTransfomer<SearchQuery, Query> getSearchQueryTransformer() {
    return transformers.getTransformer(SearchQuery.class);
  }

  @Override
  public void writeOperateMetricMigratorStep(
      final String index, final String taskId, final boolean completed) throws MigrationException {
    try {
      client.index(
          i ->
              i.index(index)
                  .id(OPERATE_MIGRATOR_STEP_TYPE)
                  .document(processorStepForKey(index, taskId, completed))
                  .refresh(Refresh.True));

    } catch (final IOException e) {
      throw new MigrationException(e);
    }
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
  public String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script) {

    try {
      if (!client.indices().exists(e -> e.index(src, dest)).value()) {
        throw new MigrationException(
            "Source %s or destination %s index does not exist".formatted(src, dest));
      }

      final var reindexRequest =
          new Builder()
              .conflicts(Conflicts.Proceed)
              .source(s -> s.index(src).query(getSearchQueryTransformer().apply(searchQuery)))
              .dest(d -> d.index(dest).opType(OpType.Create))
              .script(s -> s.source(script).lang(LANG_PAINLESS))
              .refresh(true)
              .waitForCompletion(false)
              .slices(s -> s.computed(SlicesCalculation.Auto))
              .build();
      final var response = client.reindex(reindexRequest);

      if (!response.failures().isEmpty()) {
        throw new MigrationException(
            response.failures().stream()
                .map(f -> f.cause().reason())
                .collect(Collectors.joining(",")));
      }

      return response.task();

    } catch (final IOException e) {
      throw new MigrationException(e);
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
                                f -> f.field(UsageMetricIndex.EVENT_TIME).order(SortOrder.Desc))),
            UsageMetricsEntity.class);

    final var hits = response.hits().hits();
    if (!hits.isEmpty()) {
      return hits.getFirst().source();
    }
    return null;
  }

  @Override
  public boolean getTask(final String taskId) throws MigrationException {
    try {
      final var response = client.tasks().get(r -> r.taskId(taskId));
      return response.completed();
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public MigrationStep readOperateMetricMigratorStep(final String index) throws MigrationException {
    try {
      final var response =
          client.get(b -> b.index(index).id(OPERATE_MIGRATOR_STEP_TYPE), MigrationStep.class);
      return response.found() ? response.source() : null;
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }
}
