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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.ReindexRequest.Builder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.tasks.GetTasksResponse;
import co.elastic.clients.json.JsonData;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchUsageMetricMigrationClient implements UsageMetricMigrationClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchUsageMetricMigrationClient.class);
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
  public void persistMigratorStep(
      final String index,
      final String id,
      final String content,
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
                          .document(processorStep(index, content, completed, description))
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
            .script(s -> s.source(script).lang(LANG_PAINLESS))
            .refresh(true)
            .waitForCompletion(false)
            .slices(s -> s.computed(SlicesCalculation.Auto))
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
          ? hits.stream().map(Hit::source).collect(Collectors.toSet())
          : Set.of();
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }

  @Override
  public TaskStatus getTask(final String taskId) throws MigrationException {
    final GetTasksResponse res;
    try {
      res = client.tasks().get(req -> req.taskId(taskId));
    } catch (final ElasticsearchException | IOException e) {
      if (e instanceof final ElasticsearchException ese && ese.status() == 404) {
        return TaskStatus.notFound();
      }
      throw new MigrationException(e);
    }
    if (res == null) {
      return TaskStatus.notFound();
    }
    final JsonObject status = res.task().status().toJson().asJsonObject();
    final int created = status.getInt("created");
    final int updated = status.getInt("updated");
    final int deleted = status.getInt("deleted");
    final int total = status.getInt("total");
    return new TaskStatus(
        taskId, true, res.completed(), res.task().description(), total, created, updated, deleted);
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
            .script(s -> s.source(script).lang(LANG_PAINLESS).params(paramsMap))
            .slices(s -> s.computed(SlicesCalculation.Auto))
            .refresh(true)
            .waitForCompletion(false)
            .build();

    return reindex(reindexRequest, "Reindex tasklist metric index");
  }

  @Override
  public Collection<String> getAllAssigneesInMetrics(final String index) throws IOException {
    final Set<String> assignees = new HashSet<>();

    Map<String, FieldValue> afterKey = null;
    final int pageSize = 1000;

    do {
      final Map<String, FieldValue> currentAfterKey = afterKey;

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
                                    CompositeAggregationSource.of(
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
              bucket -> assignees.add(bucket.key().get(TasklistMetricIndex.VALUE).stringValue()));

      afterKey = ca.afterKey();
    } while (afterKey != null && !afterKey.isEmpty());

    return assignees;
  }

  private String reindex(final ReindexRequest request, final String operationName) {
    try {
      final var response =
          retryDecorator.decorate(
              operationName,
              () -> client.reindex(request),
              res -> res.task() == null || res.failures() != null);
      return response.task();
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
  }
}
