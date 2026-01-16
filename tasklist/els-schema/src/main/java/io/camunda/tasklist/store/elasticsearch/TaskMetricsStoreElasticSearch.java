/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.AGGREGATION_TERMS_SIZE;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.ASSIGNEE_HASH;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.END_TIME;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.TENANT_ID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TaskMetricsStoreElasticSearch implements TaskMetricsStore {

  public static final String ASSIGNEE = "assignee";
  public static final String TU_ID_PATTERN = "%s_%s_%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMetricsStoreElasticSearch.class);

  @Autowired private UsageMetricTUTemplate template;

  @Autowired
  @Qualifier("tasklistEs8Client")
  private ElasticsearchClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

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

    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    boolQueryBuilder.must(
        QueryBuilders.range(
            r -> r.date(d -> d.field(END_TIME).gte(startTime.toString()).lt(endTime.toString()))));

    if (tenantId != null) {
      boolQueryBuilder.must(ElasticsearchUtil.termsQuery(TENANT_ID, tenantId));
    }

    final Query query = boolQueryBuilder.build()._toQuery();

    final Aggregation termsAggregation =
        Aggregation.of(a -> a.terms(t -> t.field(ASSIGNEE_HASH).size(AGGREGATION_TERMS_SIZE)));

    final SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(template.getFullQualifiedName())
                    .ignoreUnavailable(true)
                    .allowNoIndices(true)
                    .ignoreThrottled(true)
                    .expandWildcards(ExpandWildcard.Open)
                    .query(query)
                    .aggregations(ASSIGNEE, termsAggregation)
                    .size(0));

    try {
      final var response = esClient.search(searchRequest, Void.class);
      final var aggregations = response.aggregations();

      if (aggregations == null || !aggregations.containsKey(ASSIGNEE)) {
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final var termsResult = aggregations.get(ASSIGNEE).lterms();
      final List<LongTermsBucket> buckets = termsResult.buckets().array();

      return buckets.stream().map(LongTermsBucket::key).collect(Collectors.toSet());
    } catch (final IOException e) {
      LOGGER.error(
          "Error while retrieving assigned users between dates from index: " + template, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(final UsageMetricsTUEntity entity) {
    try {
      final var request =
          IndexRequest.of(
              b -> b.index(template.getFullQualifiedName()).id(entity.getId()).document(entity));

      final var response = esClient.index(request);
      final var result = response.result();

      return Result.Created.equals(result) || Result.Updated.equals(result);
    } catch (final IOException e) {
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
