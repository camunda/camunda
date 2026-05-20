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
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.HasAgentInstancesFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.report.filter.HasAgentInstancesQueryFilterOS;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
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
  private final HasAgentInstancesQueryFilterOS hasAgentInstancesQueryFilter;

  public ProcessDefinitionReaderOS(
      final DefinitionReaderOS definitionReader,
      final OptimizeOpenSearchClient osClient,
      final HasAgentInstancesQueryFilterOS hasAgentInstancesQueryFilter) {
    this.definitionReader = definitionReader;
    this.osClient = osClient;
    this.hasAgentInstancesQueryFilter = hasAgentInstancesQueryFilter;
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
    if (tenantIds.isEmpty()) {
      return Set.of();
    }

    final String defKeyAgg = "agentRunProcessDefinitionKeys";
    final List<Query> filters = new ArrayList<>();
    final List<Query> tenantFilters = new ArrayList<>();
    final List<String> nonNullTenantIds = tenantIds.stream().filter(Objects::nonNull).toList();
    if (!nonNullTenantIds.isEmpty()) {
      tenantFilters.add(QueryDSL.terms(TENANT_ID, nonNullTenantIds));
    }
    if (tenantIds.contains(null)) {
      tenantFilters.add(
          new BoolQuery.Builder().mustNot(QueryDSL.exists(TENANT_ID)).build().toQuery());
    }
    filters.add(new BoolQuery.Builder().should(tenantFilters).build().toQuery());
    filters.addAll(
        hasAgentInstancesQueryFilter.filterQueries(
            List.of(new HasAgentInstancesFilterDataDto()), FilterContext.builder().build()));
    final Query query = new BoolQuery.Builder().filter(filters).build().toQuery();

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(PROCESS_INSTANCE_MULTI_ALIAS)
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .query(query)
            .aggregations(
                defKeyAgg,
                new TermsAggregation.Builder()
                    .field(PROCESS_DEFINITION_KEY)
                    .build()
                    .toAggregation())
            .source(new SourceConfig.Builder().fetch(false).build());

    final String errorMessage = "Was not able to fetch process definitions with agent runs.";
    try {
      final SearchResponse<String> searchResponse =
          osClient.search(searchRequest, String.class, errorMessage);
      return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse, defKeyAgg);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(DefinitionType.PROCESS, e)) {
        LOG.info(
            "Was not able to retrieve process definitions with agent runs because no process instance indices exist. "
                + "Returning empty set.");
        return Set.of();
      }
      throw e;
    }
  }

  @Override
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
