/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexRequest.Builder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpensearchUsageMetricMigrationClient implements UsageMetricMigrationClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(OpensearchUsageMetricMigrationClient.class);
  private final OpenSearchClient client;
  private final OpensearchTransformers transformers;
  private final RetryDecorator retryDecorator;

  public OpensearchUsageMetricMigrationClient(
      final ConnectConfiguration connect, final RetryConfiguration retryConfiguration) {
    client = new OpensearchConnector(connect).createClient();
    transformers = new OpensearchTransformers();
    retryDecorator =
        new RetryDecorator(retryConfiguration)
            .withRetryOnException(
                e -> e instanceof IOException || e instanceof OpenSearchException);
  }

  private SearchTransfomer<SearchQuery, Query> getSearchQueryTransformer() {
    return transformers.getTransformer(SearchQuery.class);
  }

  @Override
  public void persistMigratorStep(
      final String index,
      final String id,
      final String taskId,
      final String description,
      final boolean completed)
      throws MigrationException {
    try {
      retryDecorator.decorate(
          "Write operate metric migrator step",
          () ->
              client.index(
                  i ->
                      i.index(index)
                          .id(id)
                          .document(processorStep(index, taskId, completed, description))
                          .refresh(Refresh.True)),
          res -> res.result() != Result.Created && res.result() != Result.Updated);
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script) {
    final var reindexRequest =
        new Builder()
            .conflicts(Conflicts.Proceed)
            .source(s -> s.index(src).query(getSearchQueryTransformer().apply(searchQuery)))
            .dest(d -> d.index(dest).opType(OpType.Create))
            .script(s -> s.inline(is -> is.source(script).lang(LANG_PAINLESS)))
            .refresh(true)
            .waitForCompletion(false)
            .slices(0L)
            .build();

    return reindex(reindexRequest, "Reindex operate metric index");
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
  public <T> Collection<T> findAll(
      final String index, final SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException {
    try {
      final SearchResponse<T> response =
          retryDecorator.decorate(
              "Find all",
              () ->
                  client.search(
                      b ->
                          b.index(index)
                              .size(10000)
                              .query(getSearchQueryTransformer().apply(searchQuery)),
                      entityClass),
              res -> res.hits() == null || res.hits().hits().isEmpty());

      final var hits = response.hits().hits();
      return !hits.isEmpty()
          ? hits.stream().map(Hit::source).collect(Collectors.toList())
          : Set.of();
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public TaskStatus getTask(final String taskId) throws MigrationException {
    try {
      final GetTasksResponse res;
      try {
        res = client.tasks().get(req -> req.taskId(taskId));
      } catch (final OpenSearchException e) {
        if (e.status() == 404) {
          return TaskStatus.notFound();
        }
        throw e;
      }
      if (res == null) {
        return TaskStatus.notFound();
      }
      final var status = res.task().status();
      return new TaskStatus(
          taskId,
          true,
          res.completed(),
          res.task().description(),
          status.total(),
          status.created(),
          status.updated(),
          status.deleted());
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public String reindex(
      final String src,
      final String dest,
      final SearchQuery searchQuery,
      final String script,
      final Map<String, ?> params) {
    final var paramsMap =
        params.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> JsonData.of(v.getValue())));

    final var reindexRequest =
        new Builder()
            .conflicts(Conflicts.Proceed)
            .source(s -> s.index(src).query(getSearchQueryTransformer().apply(searchQuery)))
            .dest(d -> d.index(dest).opType(OpType.Create))
            .script(s -> s.inline(is -> is.source(script).lang(LANG_PAINLESS).params(paramsMap)))
            .slices(0L)
            .refresh(true)
            .waitForCompletion(false)
            .build();

    return reindex(reindexRequest, "Reindex tasklist metric index");
  }

  @Override
  public Collection<String> getAllAssigneesInMetrics(final String index) throws IOException {
    final Set<String> assignees = new HashSet<>();

    // OpenSearch types
    Map<String, String> afterKey = null;
    final int pageSize = 1000;

    do {
      final Map<String, String> currentAfterKey = afterKey;

      final SearchRequest.Builder builder =
          new SearchRequest.Builder()
              .index(index)
              .size(0)
              .aggregations(
                  "assignees",
                  a ->
                      a.composite(
                          c -> {
                            c.sources(
                                Map.of(
                                    TasklistMetricIndex.VALUE,
                                    org.opensearch.client.opensearch._types.aggregations
                                        .CompositeAggregationSource.of(
                                        s -> s.terms(t -> t.field(TasklistMetricIndex.VALUE)))));
                            c.size(pageSize);
                            if (currentAfterKey != null) {
                              c.after(currentAfterKey);
                            }
                            return c;
                          }));

      final SearchResponse<Void> response = client.search(builder.build(), Void.class);

      final CompositeAggregate ca = response.aggregations().get("assignees").composite();

      ca.buckets()
          .array()
          .forEach(
              bucket -> {
                final var v = bucket.key().get(TasklistMetricIndex.VALUE).to(String.class);
                if (v != null) {
                  assignees.add(v);
                }
              });

      afterKey =
          ca.afterKey().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().to(String.class)));
    } while (!afterKey.isEmpty());

    return assignees;
  }

  private String reindex(final ReindexRequest reindexRequest, final String operationName) {
    try {
      final var response =
          retryDecorator.decorate(
              operationName,
              () -> client.reindex(reindexRequest),
              res -> res.task() == null || res.failures() != null);
      return response.task();
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }
}
