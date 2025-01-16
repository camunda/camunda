/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withIOException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionStore implements DecisionStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchDecisionStore.class);

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";
  @Autowired private DecisionIndex decisionIndex;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private BeanFactory beanFactory;

  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    final var indexAlias = decisionIndex.getAlias();
    final var searchRequestBuilder =
        searchRequestBuilder(indexAlias)
            .query(matchAll())
            .size(0)
            .aggregations(
                DISTINCT_FIELD_COUNTS, cardinalityAggregation(fieldName, 1_000)._toAggregation());

    try {
      final var searchResponse =
          richOpenSearchClient.doc().search(searchRequestBuilder, Void.class);

      return Optional.of(
          searchResponse.aggregations().get(DISTINCT_FIELD_COUNTS).cardinality().value());
    } catch (Exception e) {
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
  public long deleteDocuments(String indexName, String idField, String id) throws IOException {
    return withIOException(
        () -> richOpenSearchClient.doc().delete(indexName, idField, id).deleted());
  }
}
