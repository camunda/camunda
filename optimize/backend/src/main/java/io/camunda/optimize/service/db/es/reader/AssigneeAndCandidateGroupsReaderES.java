/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.util.DefinitionQueryUtilES.createDefinitionQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AssigneeAndCandidateGroupsReaderES implements AssigneeAndCandidateGroupsReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AssigneeAndCandidateGroupsReaderES.class);
  private final OptimizeElasticsearchClient esClient;

  public AssigneeAndCandidateGroupsReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public void consumeUserTaskFieldTermsInBatches(
      final String indexName,
      final String termField,
      final String termValue,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    final TermQueryBuilder filterQuery = termQuery(termField, termValue);
    consumeUserTaskFieldTermsInBatches(
        indexName, filterQuery, userTaskFieldName, termBatchConsumer, batchSize);
  }

  @Override
  public Set<String> getUserTaskFieldTerms(
      final String userTaskFieldName, final Map<String, Set<String>> definitionKeyToTenantsMap) {
    log.debug(
        "Fetching {} for process definition with key and tenants [{}]",
        userTaskFieldName,
        definitionKeyToTenantsMap);
    final Set<String> result = new HashSet<>();
    if (!definitionKeyToTenantsMap.isEmpty()) {
      final BoolQueryBuilder definitionQuery =
          createDefinitionQuery(definitionKeyToTenantsMap, PROCESS_DEFINITION_KEY, TENANT_ID);
      consumeUserTaskFieldTermsInBatches(definitionQuery, userTaskFieldName, result::addAll);
    }
    return result;
  }

  private void consumeUserTaskFieldTermsInBatches(
      final QueryBuilder filterQuery,
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
      final QueryBuilder filterQuery,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    final int resolvedBatchSize = Math.min(batchSize, MAX_RESPONSE_SIZE_LIMIT);
    final CompositeAggregationBuilder assigneeCompositeAgg =
        new CompositeAggregationBuilder(
                COMPOSITE_AGG,
                ImmutableList.of(
                    new TermsValuesSourceBuilder(TERMS_AGG)
                        .field(getUserTaskFieldPath(userTaskFieldName))))
            .size(resolvedBatchSize);
    final NestedAggregationBuilder userTasksAgg =
        nested(NESTED_USER_TASKS_AGG, FLOW_NODE_INSTANCES).subAggregation(assigneeCompositeAgg);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(filterQuery).aggregation(userTasksAgg).size(0);
    final SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

    final List<String> termsBatch = new ArrayList<>();
    final ElasticsearchCompositeAggregationScroller compositeAggregationScroller =
        ElasticsearchCompositeAggregationScroller.create()
            .setEsClient(esClient)
            .setSearchRequest(searchRequest)
            .setPathToAggregation(NESTED_USER_TASKS_AGG, COMPOSITE_AGG)
            .setCompositeBucketConsumer(
                bucket -> termsBatch.add((String) (bucket.getKey()).get(TERMS_AGG)));
    boolean hasPage;
    do {
      hasPage = compositeAggregationScroller.consumePage();
      termBatchConsumer.accept(termsBatch);
      termsBatch.clear();
    } while (hasPage);
  }
}
