/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex.EVENT;
import static io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex.VALUE;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class TaskMetricsStoreOpenSearch implements TaskMetricsStore {

  public static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  public static final String ASSIGNEE = "assignee";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMetricsStoreOpenSearch.class);

  @Autowired private TasklistMetricIndex index;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Override
  public void registerTaskCompleteEvent(final TaskEntity task) {
    final MetricEntity metric = createTaskCompleteEvent(task);
    final boolean inserted = insert(metric);
    if (!inserted) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }
  }

  @Override
  public List<String> retrieveDistinctAssigneesBetweenDates(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {

    final Query rangeQuery =
        OpenSearchUtil.joinWithAnd(
            new Query.Builder()
                .term(t -> t.field(EVENT).value(FieldValue.of(EVENT_TASK_COMPLETED_BY_ASSIGNEE))),
            new Query.Builder()
                .range(
                    r -> {
                      r.field(EVENT_TIME);

                      if (startTime != null) {
                        r.gte(JsonData.of(startTime));
                      }

                      if (endTime != null) {
                        r.lte(JsonData.of(endTime));
                      }
                      return r;
                    }));

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .expandWildcards(ExpandWildcard.Open)
            .index(index.getFullQualifiedName())
            .query(rangeQuery)
            .aggregations(ASSIGNEE, agg -> agg.terms(ta -> ta.field(VALUE).size(Integer.MAX_VALUE)))
            .build();

    try {
      final SearchResponse<Void> response = openSearchClient.search(searchRequest, Void.class);
      final Map<String, Aggregate> aggregations = response.aggregations();

      if (CollectionUtils.isEmpty(aggregations)) {
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregate aggregate = aggregations.get(ASSIGNEE);
      if (!aggregate.isSterms()) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final List<StringTermsBucket> buckets = aggregate.sterms().buckets().array();
      return buckets.stream().map(StringTermsBucket::key).toList();
    } catch (final IOException | OpenSearchException e) {
      LOGGER.error("Error while retrieving assigned users between dates from index: " + index, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(final MetricEntity entity) {
    try {
      final IndexRequest<MetricEntity> request =
          IndexRequest.of(
              builder ->
                  builder.index(index.getFullQualifiedName()).id(entity.getId()).document(entity));

      final IndexResponse response = openSearchClient.index(request);
      return Result.Created.equals(response.result());
    } catch (final IOException | OpenSearchException e) {
      LOGGER.error(e.getMessage(), e);
      throw new TasklistRuntimeException("Error while trying to upsert entity: " + entity);
    }
  }

  private MetricEntity createTaskCompleteEvent(final TaskEntity task) {
    return new MetricEntity()
        .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE)
        .setValue(task.getAssignee())
        .setEventTime(task.getCompletionTime())
        .setTenantId(task.getTenantId());
  }
}
