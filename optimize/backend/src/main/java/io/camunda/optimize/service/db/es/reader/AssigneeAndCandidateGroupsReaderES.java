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
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeTermsAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AssigneeAndCandidateGroupsReaderES implements AssigneeAndCandidateGroupsReader {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AssigneeAndCandidateGroupsReaderES.class);
  private final OptimizeElasticsearchClient esClient;

  public AssigneeAndCandidateGroupsReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public void consumeAssigneesInBatches(
      final String engineAlias,
      final Consumer<List<String>> assigneeBatchConsumer,
      final int batchSize) {
    if (engineAlias == null) {
      throw new IllegalArgumentException("engineAlias cannot be null");
    }
    if (assigneeBatchConsumer == null) {
      throw new IllegalArgumentException("assigneeBatchConsumer cannot be null");
    }

    consumeUserTaskFieldTermsInBatches(
        Query.of(
            q ->
                q.term(
                    t ->
                        t.field(
                                ProcessInstanceDto.Fields.dataSource
                                    + "."
                                    + DataSourceDto.Fields.name)
                            .value(engineAlias))),
        USER_TASK_ASSIGNEE,
        assigneeBatchConsumer,
        batchSize);
  }

  @Override
  public void consumeCandidateGroupsInBatches(
      final String engineAlias,
      final Consumer<List<String>> candidateGroupBatchConsumer,
      final int batchSize) {
    if (engineAlias == null) {
      throw new IllegalArgumentException("engineAlias cannot be null");
    }
    if (candidateGroupBatchConsumer == null) {
      throw new IllegalArgumentException("candidateGroupBatchConsumer cannot be null");
    }

    consumeUserTaskFieldTermsInBatches(
        Query.of(
            q ->
                q.term(
                    t ->
                        t.field(
                                ProcessInstanceDto.Fields.dataSource
                                    + "."
                                    + DataSourceDto.Fields.name)
                            .value(engineAlias))),
        USER_TASK_CANDIDATE_GROUPS,
        candidateGroupBatchConsumer,
        batchSize);
  }

  @Override
  public Set<String> getAssigneeIdsForProcess(
      final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(
        ProcessInstanceIndex.USER_TASK_ASSIGNEE, definitionKeyToTenantsMap);
  }

  @Override
  public Set<String> getCandidateGroupIdsForProcess(
      final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(
        ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS, definitionKeyToTenantsMap);
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
          DefinitionQueryUtilES.createDefinitionQuery(
              definitionKeyToTenantsMap, PROCESS_DEFINITION_KEY, TENANT_ID);
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
    consumeUserTaskFieldTermsInBatches(
        indexName,
        Query.of(q -> q.term(t -> t.field(termField).value(termValue))),
        userTaskFieldName,
        termBatchConsumer,
        batchSize);
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
      final Query filterQuery,
      final String fieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    consumeUserTaskFieldTermsInBatches(
        PROCESS_INSTANCE_MULTI_ALIAS, filterQuery, fieldName, termBatchConsumer, batchSize);
  }

  public void consumeUserTaskFieldTermsInBatches(
      final String indexName,
      final Query filterQuery,
      final String fieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    final int resolvedBatchSize = Math.min(batchSize, MAX_RESPONSE_SIZE_LIMIT);

    final Function<Map<String, FieldValue>, SearchRequest> aggregationRequestWithAfterKeys =
        (map) ->
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, indexName)
                        .query(filterQuery)
                        .size(0)
                        .aggregations(
                            NESTED_USER_TASKS_AGG,
                            Aggregation.of(
                                a ->
                                    a.nested(NestedAggregation.of(n -> n.path(FLOW_NODE_INSTANCES)))
                                        .aggregations(
                                            COMPOSITE_AGG,
                                            Aggregation.of(
                                                aa ->
                                                    aa.composite(
                                                        CompositeAggregation.of(
                                                            c -> {
                                                              c.sources(
                                                                      ImmutableList.of(
                                                                          Map.of(
                                                                              TERMS_AGG,
                                                                              CompositeAggregationSource
                                                                                  .of(
                                                                                      cc ->
                                                                                          cc.terms(
                                                                                              CompositeTermsAggregation
                                                                                                  .of(
                                                                                                      ct ->
                                                                                                          ct.field(
                                                                                                                  getUserTaskFieldPath(
                                                                                                                      fieldName))
                                                                                                              .missingBucket(
                                                                                                                  false)
                                                                                                              .order(
                                                                                                                  SortOrder
                                                                                                                      .Asc)))))))
                                                                  .size(resolvedBatchSize);
                                                              if (map != null) {
                                                                c.after(map);
                                                              }
                                                              return c;
                                                            })))))));

    final List<String> termsBatch = new ArrayList<>();
    final ElasticsearchCompositeAggregationScroller compositeAggregationScroller =
        ElasticsearchCompositeAggregationScroller.create()
            .setEsClient(esClient)
            .setSearchRequest(aggregationRequestWithAfterKeys.apply(null))
            .setFunction(aggregationRequestWithAfterKeys)
            .setPathToAggregation(NESTED_USER_TASKS_AGG, COMPOSITE_AGG)
            .setCompositeBucketConsumer(
                bucket -> termsBatch.add(bucket.key().get(TERMS_AGG).stringValue()));
    boolean hasPage;
    do {
      hasPage = compositeAggregationScroller.consumePage();
      termBatchConsumer.accept(termsBatch);
      termsBatch.clear();
    } while (hasPage);
  }
}
