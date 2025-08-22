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
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
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
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;

public final class ElasticsearchUsageMetricMigrationClient implements UsageMetricMigrationClient {

  private final ElasticsearchClient client;
  private final ElasticsearchTransformers transformers;
  private final RetryDecorator retryDecorator;

  public ElasticsearchUsageMetricMigrationClient(
      final ConnectConfiguration connect, final RetryConfiguration retryConfiguration) {
    client = new ElasticsearchConnector(connect).createClient();
    transformers = new ElasticsearchTransformers();
    retryDecorator =
        new RetryDecorator(retryConfiguration)
            .withRetryOnException(
                e -> e instanceof IOException || e instanceof ElasticsearchException);
  }

  private SearchTransfomer<SearchQuery, Query> getSearchQueryTransformer() {
    return transformers.getTransformer(SearchQuery.class);
  }

  @Override
  public void writeOperateMetricMigratorStep(
      final String index, final String taskId, final boolean completed) throws MigrationException {
    try {
      retryDecorator.decorate(
          "Write operate metric migrator step",
          () ->
              client.index(
                  i ->
                      i.index(index)
                          .id(OPERATE_MIGRATOR_STEP_ID)
                          .document(migrationStepForKey(index, taskId, completed))
                          .refresh(Refresh.True)),
          res -> res.result() != Result.Created && res.result() != Result.Updated);
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script) {

    try {
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

      final var response =
          retryDecorator.decorate(
              "Reindex operate metric index",
              () -> client.reindex(reindexRequest),
              res -> res.task() == null || res.failures() != null);
      return response.task();
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public <T> T findOne(
      final String index, final SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException {

    try {
      final SearchResponse<T> response =
          retryDecorator.decorate(
              "Find one entity",
              () ->
                  client.search(
                      b ->
                          b.index(index)
                              .from(0)
                              .size(1)
                              .query(getSearchQueryTransformer().apply(searchQuery)),
                      entityClass),
              res -> res.hits() == null || res.hits().hits().isEmpty());

      final var hits = response.hits().hits();
      return !hits.isEmpty() ? hits.getFirst().source() : null;
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public boolean hasTaskSuccessfullyCompleted(final String taskId) throws MigrationException {
    try {
      final var response =
          retryDecorator.decorate(
              "Has task successfully completed",
              () -> client.tasks().get(r -> r.taskId(taskId)),
              res -> false);
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
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }
}
