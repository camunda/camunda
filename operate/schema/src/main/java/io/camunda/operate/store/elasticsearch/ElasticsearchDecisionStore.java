/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchDecisionStore implements DecisionStore {

  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchDecisionStore.class);

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private ElasticsearchClient es8Client;

  @Override
  public Optional<Long> getDistinctCountFor(final String fieldName) {
    final String indexAlias = decisionIndex.getAlias();
    LOGGER.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);

    final var searchRequest =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(indexAlias)
            .query(q -> q.matchAll(m -> m))
            .size(0)
            .aggregations(
                DISTINCT_FIELD_COUNTS,
                a -> a.cardinality(c -> c.precisionThreshold(1_000).field(fieldName)))
            .build();
    try {
      final var res = es8Client.search(searchRequest, Void.class);
      final var distinctFieldCounts = res.aggregations().get(DISTINCT_FIELD_COUNTS).cardinality();

      return Optional.of(distinctFieldCounts.value());
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
    return null;
  }

  @Override
  public long deleteDocuments(final String indexName, final String idField, final String id)
      throws IOException {

    final var res =
        es8Client.deleteByQuery(
            d -> d.index(indexName).query(ElasticsearchUtil.termsQuery(idField, id)));

    LOGGER.debug("Delete document {} in {} response: {}", id, indexName, res.deleted());
    return res.deleted() != null ? res.deleted() : 0L;
  }
}
