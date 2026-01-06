/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.OpenSearchUtil.AGGREGATION_TERMS_SIZE;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.ASSIGNEE_HASH;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.END_TIME;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.Query.Builder;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.util.ObjectBuilder;
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

  public static final String ASSIGNEE = "assignee";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMetricsStoreOpenSearch.class);
  private static final String TU_ID_PATTERN = "%s_%s_%s";

  @Autowired private UsageMetricTUTemplate template;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Override
  public void registerTaskAssigned(final TaskEntity task) {
    final UsageMetricsTUEntity metric = createTaskAssignedEntity(task);
    final boolean inserted = insert(metric);
    if (!inserted) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }
  }

  @Override
  public Set<Long> retrieveDistinctAssigneesBetweenDates(
      final OffsetDateTime startTime, final OffsetDateTime endTime, final String tenantId) {

    final ObjectBuilder<Query> queryBuilder =
        new Builder()
            .range(
                r -> {
                  r.field(END_TIME);

                  if (startTime != null) {
                    r.gte(JsonData.of(startTime));
                  }

                  if (endTime != null) {
                    r.lt(JsonData.of(endTime));
                  }
                  return r;
                });
    final Query query =
        tenantId != null
            ? OpenSearchUtil.joinWithAnd(
                queryBuilder,
                new Builder()
                    .term(
                        t ->
                            t.field(UsageMetricTUTemplate.TENANT_ID)
                                .value(FieldValue.of(tenantId))))
            : queryBuilder.build();

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .expandWildcards(ExpandWildcard.Open)
            .index(template.getFullQualifiedName())
            .query(query)
            .aggregations(
                ASSIGNEE,
                agg -> agg.terms(ta -> ta.field(ASSIGNEE_HASH).size(AGGREGATION_TERMS_SIZE)))
            .build();

    try {
      final SearchResponse<Void> response = openSearchClient.search(searchRequest, Void.class);
      final Map<String, Aggregate> aggregations = response.aggregations();

      if (CollectionUtils.isEmpty(aggregations)) {
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregate aggregate = aggregations.get(ASSIGNEE);
      if (!aggregate.isLterms()) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final List<LongTermsBucket> buckets = aggregate.lterms().buckets().array();
      return buckets.stream().map(l -> Long.valueOf(l.key())).collect(Collectors.toSet());
    } catch (final IOException | OpenSearchException e) {
      LOGGER.error(
          "Error while retrieving assigned users between dates from index: " + template, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(final UsageMetricsTUEntity entity) {
    try {
      final IndexRequest<UsageMetricsTUEntity> request =
          IndexRequest.of(
              builder ->
                  builder
                      .index(template.getFullQualifiedName())
                      .id(entity.getId())
                      .document(entity));

      final IndexResponse response = openSearchClient.index(request);
      final Result result = response.result();

      return Result.Created.equals(result) || Result.Updated.equals(result);
    } catch (final IOException | OpenSearchException e) {
      LOGGER.error(e.getMessage(), e);
      throw new TasklistRuntimeException("Error while trying to upsert entity: " + entity);
    }
  }

  private UsageMetricsTUEntity createTaskAssignedEntity(final TaskEntity task) {
    final String tenantId = task.getTenantId();
    final long assigneeHash = HashUtil.getStringHashValue(task.getAssignee());
    return new UsageMetricsTUEntity()
        .setId(String.format(TU_ID_PATTERN, task.getKey(), tenantId, assigneeHash))
        .setStartTime(task.getCreationTime())
        .setEndTime(task.getCreationTime())
        .setAssigneeHash(assigneeHash)
        .setTenantId(tenantId)
        .setPartitionId(task.getPartitionId());
  }
}
