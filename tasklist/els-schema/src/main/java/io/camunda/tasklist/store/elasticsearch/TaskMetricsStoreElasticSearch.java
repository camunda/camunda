/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.LENIENT_EXPAND_OPEN_IGNORE_THROTTLED;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex.ASSIGNEE_HASH;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex.TENANT_ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
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

  @Autowired private UsageMetricTUIndex index;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

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

    final BoolQueryBuilder boolQuery =
        boolQuery().must(QueryBuilders.rangeQuery(EVENT_TIME).gte(startTime).lte(endTime));

    if (tenantId != null) {
      boolQuery.must(QueryBuilders.termQuery(TENANT_ID, tenantId));
    }

    final TermsAggregationBuilder aggregation =
        AggregationBuilders.terms(ASSIGNEE).field(ASSIGNEE_HASH).size(Integer.MAX_VALUE);

    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource().query(boolQuery).aggregation(aggregation);
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(LENIENT_EXPAND_OPEN_IGNORE_THROTTLED)
            .source(source);
    try {

      final Aggregations aggregations =
          esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations();
      if (aggregations == null) {
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregation group = aggregations.get(ASSIGNEE);
      if (!(group instanceof final ParsedLongTerms terms)) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final List<ParsedLongTerms.ParsedBucket> buckets =
          (List<ParsedLongTerms.ParsedBucket>) terms.getBuckets();

      return buckets.stream().map(it -> (long) it.getKey()).collect(Collectors.toSet());
    } catch (final IOException e) {
      LOGGER.error("Error while retrieving assigned users between dates from index: " + index, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(final UsageMetricsTUEntity entity) {
    try {
      final IndexRequest request =
          new IndexRequest(index.getFullQualifiedName())
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON);

      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      return response.status() == RestStatus.CREATED;
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
        .setEventTime(task.getCreationTime())
        .setAssigneeHash(assigneeHash)
        .setTenantId(tenantId)
        .setPartitionId(task.getPartitionId());
  }
}
