/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.service.es.CompositeAggregationScroller;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
@Component
@Slf4j
public class AssigneeAndCandidateGroupsReader {

  private static final String NESTED_USER_TASKS_AGG = "userTasks";
  private static final String COMPOSITE_AGG = "composite";
  private static final String TERMS_AGG = "userTaskFieldTerms";

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;

  public void consumeAssigneesInBatches(@NonNull final String engineAlias,
                                        @NonNull final Consumer<List<String>> assigneeBatchConsumer,
                                        final int batchSize) {
    consumeUserTaskFieldTermsInBatches(
      termQuery(ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.name, engineAlias),
      USER_TASK_ASSIGNEE,
      assigneeBatchConsumer,
      batchSize
    );
  }

  public void consumeCandidateGroupsInBatches(@NonNull final String engineAlias,
                                              @NonNull final Consumer<List<String>> candidateGroupBatchConsumer,
                                              final int batchSize) {

    consumeUserTaskFieldTermsInBatches(
      termQuery(ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.name, engineAlias),
      USER_TASK_CANDIDATE_GROUPS,
      candidateGroupBatchConsumer,
      batchSize
    );
  }

  public Set<String> getAssigneeIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(ProcessInstanceIndex.USER_TASK_ASSIGNEE, definitionKeyToTenantsMap);
  }

  public Set<String> getCandidateGroupIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS, definitionKeyToTenantsMap);
  }

  private Set<String> getUserTaskFieldTerms(final String userTaskFieldName,
                                            final Map<String, Set<String>> definitionKeyToTenantsMap) {
    log.debug(
      "Fetching {} for process definition with key and tenants [{}]", userTaskFieldName, definitionKeyToTenantsMap
    );
    final Set<String> result = new HashSet<>();
    if (!definitionKeyToTenantsMap.isEmpty()) {
      final BoolQueryBuilder definitionQuery = createDefinitionQuery(
        definitionKeyToTenantsMap, PROCESS_DEFINITION_KEY, TENANT_ID
      );
      consumeUserTaskFieldTermsInBatches(
        definitionQuery,
        userTaskFieldName,
        result::addAll
      );
    }
    return result;
  }

  private void consumeUserTaskFieldTermsInBatches(final QueryBuilder filterQuery,
                                                  final String fieldName,
                                                  final Consumer<List<String>> termBatchConsumer) {
    consumeUserTaskFieldTermsInBatches(
      PROCESS_INSTANCE_MULTI_ALIAS,
      filterQuery,
      fieldName,
      termBatchConsumer,
      MAX_RESPONSE_SIZE_LIMIT
    );
  }

  private void consumeUserTaskFieldTermsInBatches(final QueryBuilder filterQuery,
                                                  final String fieldName,
                                                  final Consumer<List<String>> termBatchConsumer,
                                                  final int batchSize) {
    consumeUserTaskFieldTermsInBatches(
      PROCESS_INSTANCE_MULTI_ALIAS,
      filterQuery,
      fieldName,
      termBatchConsumer,
      batchSize
    );
  }


  private void consumeUserTaskFieldTermsInBatches(final String indexName,
                                                  final QueryBuilder filterQuery,
                                                  final String fieldName,
                                                  final Consumer<List<String>> termBatchConsumer,
                                                  final int batchSize) {
    final int resolvedBatchSize = Math.min(batchSize, MAX_RESPONSE_SIZE_LIMIT);
    final CompositeAggregationBuilder assigneeCompositeAgg = new CompositeAggregationBuilder(
      COMPOSITE_AGG, ImmutableList.of(new TermsValuesSourceBuilder(TERMS_AGG).field(getUserTaskFieldPath(fieldName)))
    ).size(resolvedBatchSize);
    final NestedAggregationBuilder userTasksAgg = nested(NESTED_USER_TASKS_AGG, FLOW_NODE_INSTANCES)
      .subAggregation(assigneeCompositeAgg);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .aggregation(userTasksAgg)
      .size(0);
    final SearchRequest searchRequest =
      new SearchRequest(indexName).source(searchSourceBuilder);

    final List<String> termsBatch = new ArrayList<>();
    final CompositeAggregationScroller compositeAggregationScroller = CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(NESTED_USER_TASKS_AGG, COMPOSITE_AGG)
      .setCompositeBucketConsumer(bucket -> termsBatch.add((String) (bucket.getKey()).get(TERMS_AGG)));
    boolean hasPage;
    do {
      hasPage = compositeAggregationScroller.consumePage();
      termBatchConsumer.accept(termsBatch);
      termsBatch.clear();
    } while (hasPage);
  }

  private String getUserTaskFieldPath(final String fieldName) {
    return FLOW_NODE_INSTANCES + "." + fieldName;
  }

}
