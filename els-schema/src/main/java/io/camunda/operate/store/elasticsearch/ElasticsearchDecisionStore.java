/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;

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

import java.io.IOException;
import java.util.Optional;

import static org.elasticsearch.search.aggregations.AggregationBuilders.cardinality;


@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchDecisionStore implements DecisionStore {

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchDecisionStore.class);

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private TenantAwareElasticsearchClient tenantAwareClient;

  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    final String indexAlias = decisionIndex.getAlias();
    logger.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final SearchRequest searchRequest = new SearchRequest(indexAlias)
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery()).size(0)
            .aggregation(
                cardinality(DISTINCT_FIELD_COUNTS)
                    .precisionThreshold(1_000)
                    .field(fieldName)));
    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final Cardinality distinctFieldCounts = searchResponse.getAggregations().get(DISTINCT_FIELD_COUNTS);
      return Optional.of(distinctFieldCounts.getValue());
    } catch (Exception e) {
      logger.error(String.format("Error in distinct count for field %s in index alias %s.", fieldName, indexAlias), e);
      return Optional.empty();
    }
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  @Override
  public long deleteDocuments(String indexName, String idField, String id) throws IOException {
    final DeleteByQueryRequest query = new DeleteByQueryRequest(indexName)
        .setQuery(QueryBuilders.termsQuery(idField, id));
    BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
    logger.debug("Delete document {} in {} response: {}", id, indexName, response.getStatus());
    return response.getDeleted();
  }
}
