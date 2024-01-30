/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withIOException;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionStore implements DecisionStore {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchDecisionStore.class);

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";
  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private BeanFactory beanFactory;


  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    var indexAlias = decisionIndex.getAlias();
    var searchRequestBuilder = searchRequestBuilder(indexAlias)
      .query(withTenantCheck(matchAll()))
      .size(0)
      .aggregations(DISTINCT_FIELD_COUNTS, cardinalityAggregation(fieldName, 1_000)._toAggregation());

    try {
      var searchResponse = richOpenSearchClient.doc().search(searchRequestBuilder, Void.class);

      return Optional.of(searchResponse
        .aggregations()
        .get(DISTINCT_FIELD_COUNTS)
        .cardinality()
        .value());
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
    return withIOException( () ->
      richOpenSearchClient.doc().delete(indexName, idField, id).deleted()
    );
  }
}
