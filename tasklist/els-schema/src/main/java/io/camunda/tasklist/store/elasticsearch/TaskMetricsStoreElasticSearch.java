/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.LENIENT_EXPAND_OPEN_IGNORE_THROTTLED;
import static io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex.EVENT;
import static io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex.VALUE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
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

  public static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  public static final String ASSIGNEE = "assignee";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMetricsStoreElasticSearch.class);
  @Autowired private TasklistMetricIndex index;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void registerTaskCompleteEvent(TaskEntity task) {
    final MetricEntity metric = createTaskCompleteEvent(task);
    final boolean inserted = insert(metric);
    if (!inserted) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(MetricEntity entity) {
    try {
      final IndexRequest request =
          new IndexRequest(index.getFullQualifiedName())
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON);

      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      return response.status() == RestStatus.CREATED;
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      throw new TasklistRuntimeException("Error while trying to upsert entity: " + entity);
    }
  }

  @Override
  public List<String> retrieveDistinctAssigneesBetweenDates(
      OffsetDateTime startTime, OffsetDateTime endTime) {

    final BoolQueryBuilder rangeQuery =
        boolQuery()
            .must(QueryBuilders.termsQuery(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE))
            .must(QueryBuilders.rangeQuery(EVENT_TIME).gte(startTime).lte(endTime));
    final TermsAggregationBuilder aggregation =
        AggregationBuilders.terms(ASSIGNEE).field(VALUE).size(Integer.MAX_VALUE);

    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource().query(rangeQuery).aggregation(aggregation);
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
      if (!(group instanceof ParsedStringTerms terms)) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final List<ParsedStringTerms.ParsedBucket> buckets =
          (List<ParsedStringTerms.ParsedBucket>) terms.getBuckets();

      return buckets.stream().map(it -> String.valueOf(it.getKey())).collect(Collectors.toList());
    } catch (IOException e) {
      LOGGER.error("Error while retrieving assigned users between dates from index: " + index, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private MetricEntity createTaskCompleteEvent(TaskEntity task) {
    return new MetricEntity()
        .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE)
        .setValue(task.getAssignee())
        .setEventTime(task.getCompletionTime())
        .setTenantId(task.getTenantId());
  }
}
