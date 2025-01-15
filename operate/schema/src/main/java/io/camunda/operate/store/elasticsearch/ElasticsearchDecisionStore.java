/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static org.elasticsearch.search.aggregations.AggregationBuilders.cardinality;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import java.io.IOException;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchDecisionStore implements DecisionStore {

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchDecisionStore.class);

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private BeanFactory beanFactory;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Override
  public Optional<Long> getDistinctCountFor(final String fieldName) {
    final String indexAlias = decisionIndex.getAlias();
    LOGGER.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final SearchRequest searchRequest =
        new SearchRequest(indexAlias)
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(0)
                    .aggregation(
                        cardinality(DISTINCT_FIELD_COUNTS)
                            .precisionThreshold(1_000)
                            .field(fieldName)));
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Cardinality distinctFieldCounts =
          searchResponse.getAggregations().get(DISTINCT_FIELD_COUNTS);
      return Optional.of(distinctFieldCounts.getValue());
    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error in distinct count for field %s in index alias %s.", fieldName, indexAlias),
          e);
      return Optional.empty();
    }
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  @Override
  public long deleteDocuments(final String indexName, final String idField, final String id)
      throws IOException {
    final DeleteByQueryRequest query =
        new DeleteByQueryRequest(indexName).setQuery(QueryBuilders.termsQuery(idField, id));
    final BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
    LOGGER.debug("Delete document {} in {} response: {}", id, indexName, response.getStatus());
    return response.getDeleted();
  }
}
