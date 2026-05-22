/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionReaderOS implements ProcessDefinitionReader {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionReaderOS.class);
  private final DefinitionReaderOS definitionReader;
  private final OptimizeOpenSearchClient osClient;

  public ProcessDefinitionReaderOS(
      final DefinitionReaderOS definitionReader, final OptimizeOpenSearchClient osClient) {
    this.definitionReader = definitionReader;
    this.osClient = osClient;
  }

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    final BoolQuery query =
        new BoolQuery.Builder()
            .must(QueryDSL.matchAll())
            .must(QueryDSL.term(PROCESS_DEFINITION_ID, definitionId))
            .build();
    return definitionReader.getDefinitions(DefinitionType.PROCESS, query, true).stream()
        .findFirst()
        .map(ProcessDefinitionOptimizeDto.class::cast);
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    final String defKeyAgg = "keyAgg";
    final Query query =
        new BoolQuery.Builder()
            .must(QueryDSL.term(ProcessDefinitionIndex.ONBOARDED, false))
            .must(QueryDSL.term(DEFINITION_DELETED, false))
            .should(QueryDSL.exists(PROCESS_DEFINITION_XML))
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(PROCESS_DEFINITION_INDEX_NAME)
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .query(query)
            .aggregations(
                defKeyAgg,
                new TermsAggregation.Builder()
                    .field(ProcessDefinitionIndex.PROCESS_DEFINITION_KEY)
                    .build()
                    .toAggregation())
            .source(new SourceConfig.Builder().fetch(false).build());

    final String errorMessage = "Was not able to fetch non-onboarded process definition keys.";
    final SearchResponse<String> searchResponse =
        osClient.search(searchRequest, String.class, errorMessage);
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse, defKeyAgg);
  }

  @Override
  public Set<String> getProcessDefinitionsWithAgentRuns(final List<String> tenantIds) {
    final String defKeyAgg = "defKeyAgg";

    final BoolQuery.Builder boolQuery = new BoolQuery.Builder();
    boolQuery.filter(
        Query.of(
            q ->
                q.nested(
                    NestedQuery.of(
                        n ->
                            n.path(AGENT_INSTANCES)
                                .query(QueryDSL.matchAll())
                                .scoreMode(ChildScoreMode.None)))));
    if (tenantIds != null && !tenantIds.isEmpty()) {
      boolQuery.filter(QueryDSL.stringTerms(TENANT_ID, tenantIds));
    }

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(PROCESS_INSTANCE_MULTI_ALIAS)
            .size(0)
            .query(boolQuery.build().toQuery())
            .aggregations(
                defKeyAgg,
                new TermsAggregation.Builder()
                    .field(PROCESS_DEFINITION_KEY)
                    .size(MAX_RESPONSE_SIZE_LIMIT)
                    .build()
                    .toAggregation())
            .source(new SourceConfig.Builder().fetch(false).build());

    final String errorMessage = "Was not able to fetch process definition keys with agent runs.";
    final SearchResponse<String> searchResponse =
        osClient.search(searchRequest, String.class, errorMessage);
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse, defKeyAgg);
  }

  @Override
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
