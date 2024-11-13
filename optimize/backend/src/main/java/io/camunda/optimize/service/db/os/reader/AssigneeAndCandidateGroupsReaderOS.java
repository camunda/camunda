/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.util.DefinitionQueryUtilOS.createDefinitionQuery;

import io.camunda.optimize.service.db.os.OpenSearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeTermsAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AssigneeAndCandidateGroupsReaderOS implements AssigneeAndCandidateGroupsReader {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AssigneeAndCandidateGroupsReaderOS.class);
  private final OptimizeOpenSearchClient osClient;

  public AssigneeAndCandidateGroupsReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Set<String> getUserTaskFieldTerms(
      final String userTaskFieldName, final Map<String, Set<String>> definitionKeyToTenantsMap) {
    LOG.debug(
        "Fetching {} for process definition with key and tenants [{}]",
        userTaskFieldName,
        definitionKeyToTenantsMap);
    final Set<String> result = new HashSet<>();
    if (!definitionKeyToTenantsMap.isEmpty()) {
      final Query definitionQuery =
          createDefinitionQuery(definitionKeyToTenantsMap, PROCESS_DEFINITION_KEY, TENANT_ID);
      consumeUserTaskFieldTermsInBatches(definitionQuery, userTaskFieldName, result::addAll);
    }
    return result;
  }

  @Override
  public void consumeUserTaskFieldTermsInBatches(
      final String indexName,
      final String termField,
      final String termValue,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    final Query filterQuery = QueryDSL.term(termField, termValue);
    consumeUserTaskFieldTermsInBatches(
        indexName, filterQuery, userTaskFieldName, termBatchConsumer, batchSize);
  }

  private void consumeUserTaskFieldTermsInBatches(
      final Query filterQuery,
      final String fieldName,
      final Consumer<List<String>> termBatchConsumer) {
    consumeUserTaskFieldTermsInBatches(
        PROCESS_INSTANCE_MULTI_ALIAS,
        filterQuery,
        fieldName,
        termBatchConsumer,
        MAX_RESPONSE_SIZE_LIMIT);
  }

  private void consumeUserTaskFieldTermsInBatches(
      final String indexName,
      final Query filterQuery,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    final int resolvedBatchSize = Math.min(batchSize, MAX_RESPONSE_SIZE_LIMIT);
    final List<Map<String, CompositeAggregationSource>> sources = new ArrayList<>();
    sources.add(
        Collections.singletonMap(
            TERMS_AGG,
            new CompositeAggregationSource.Builder()
                .terms(
                    new CompositeTermsAggregationSource.Builder()
                        .field(getUserTaskFieldPath(userTaskFieldName))
                        .build())
                .build()));
    final CompositeAggregation assigneeCompositeAgg =
        new CompositeAggregation.Builder().sources(sources).size(resolvedBatchSize).build();

    final NestedAggregation nestedAgg = new Builder().path(FLOW_NODE_INSTANCES).build();

    final Aggregation userTasksAgg =
        AggregationDSL.withSubaggregations(
            nestedAgg,
            Collections.singletonMap(COMPOSITE_AGG, assigneeCompositeAgg._toAggregation()));

    final List<String> termsBatch = new ArrayList<>();
    final OpenSearchCompositeAggregationScroller compositeAggregationScroller =
        OpenSearchCompositeAggregationScroller.create()
            .setClient(osClient)
            .query(filterQuery)
            .aggregations(Map.of(NESTED_USER_TASKS_AGG, userTasksAgg))
            .index(List.of(indexName))
            .size(0)
            .setPathToAggregation(NESTED_USER_TASKS_AGG, COMPOSITE_AGG)
            .setCompositeBucketConsumer(
                bucket -> termsBatch.add((bucket.key()).get(TERMS_AGG).to(String.class)));
    boolean hasPage;
    do {
      hasPage = compositeAggregationScroller.consumePage();
      termBatchConsumer.accept(termsBatch);
      termsBatch.clear();
    } while (hasPage);
  }
}
