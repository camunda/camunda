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
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexRequest.Builder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.io.IOException;
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
                  .id(OPERATE_MIGRATOR_STEP_ID)
                  .document(migrationStepForKey(index, taskId, completed))
                  .refresh(Refresh.True));

    } catch (final IOException e) {
      throw new MigrationException(e);
    }
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
      return response.task();

    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public <T> T findOne(
      final String index, final SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException {

    try {
      final SearchResponse<T> response =
          client.search(
              b ->
                  b.index(index)
                      .from(0)
                      .size(1)
                      .query(getSearchQueryTransformer().apply(searchQuery)),
              entityClass);

      final var hits = response.hits().hits();
      return !hits.isEmpty() ? hits.getFirst().source() : null;
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public boolean getTask(final String taskId) throws MigrationException {
    try {
      final var response = client.tasks().get(r -> r.taskId(taskId));
      if (response.completed()) {
        if (response.error() != null) {
          final var cause =
              response.error().causedBy() != null ? response.error().causedBy() : response.error();
          throw new MigrationException(cause.type() + ": " + cause.reason());
        } else {
          return true;
        }
      } else {
        return false;
      }
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }
}
