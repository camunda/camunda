/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.OUTPUTS;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.decision.AbstractDecisionViewRawDataInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class DecisionViewRawDataInterpreterES extends AbstractDecisionViewRawDataInterpreter
    implements DecisionViewInterpreterES {
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  @Getter private final DecisionVariableReader decisionVariableReader;

  @Override
  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final SearchSourceBuilder search = searchRequest.source().fetchSource(true);
    context
        .getPagination()
        .ifPresent(
            pag -> {
              if (context.isCsvExport()) {
                search.size(
                    pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT
                        ? MAX_RESPONSE_SIZE_LIMIT
                        : pag.getLimit());
                searchRequest.scroll(
                    timeValueSeconds(
                        configurationService
                            .getElasticSearchConfiguration()
                            .getScrollTimeoutInSeconds()));
              } else {
                if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
                  pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
                }
                search.size(pag.getLimit()).from(pag.getOffset());
              }
            });

    addSortingToQuery(context.getReportData(), searchRequest.source());
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Collections.emptyList();
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos;
    if (context.isCsvExport()) {
      rawDataDecisionInstanceDtos =
          ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
              response,
              DecisionInstanceDto.class,
              objectMapper,
              esClient,
              configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
              context.getPagination().orElse(new PaginationDto()).getLimit());
    } else {
      rawDataDecisionInstanceDtos =
          ElasticsearchReaderUtil.mapHits(
              response.getHits(), DecisionInstanceDto.class, objectMapper);
    }
    final List<RawDataDecisionInstanceDto> rawData =
        rawDataSingleReportResultDtoMapper.mapFrom(
            rawDataDecisionInstanceDtos,
            getInputVariableEntries(context.getReportData()),
            getOutputVars(context.getReportData()));
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  private void addSortingToQuery(
      final DecisionReportDataDto decisionReportData,
      final SearchSourceBuilder searchRequestBuilder) {
    final Optional<ReportSortingDto> customSorting =
        decisionReportData.getConfiguration().getSorting();
    final String sortByField =
        customSorting
            .flatMap(ReportSortingDto::getBy)
            .orElse(DecisionInstanceIndex.EVALUATION_DATE_TIME);
    final SortOrder sortOrder =
        customSorting
            .flatMap(ReportSortingDto::getOrder)
            .map(order -> SortOrder.valueOf(order.name()))
            .orElse(SortOrder.DESC);

    if (sortByField.startsWith(INPUT_VARIABLE_PREFIX)) {
      addSortByInputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else if (sortByField.startsWith(OUTPUT_VARIABLE_PREFIX)) {
      addSortByOutputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else {
      searchRequestBuilder.sort(
          SortBuilders.fieldSort(sortByField)
              .order(sortOrder)
              // this ensures the query doesn't fail on unknown properties but just ignores them
              // this is done to ensure consistent behavior compared to unknown variable names as ES
              // doesn't fail there
              // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
              .unmappedType("short"));
    }
  }

  private void addSortByInputVariable(
      final SearchSourceBuilder searchRequestBuilder,
      final String sortByField,
      final SortOrder sortOrder) {
    getVariableMultivalueFields()
        .forEach(
            type ->
                searchRequestBuilder.sort(
                    createSortByVariable(
                        sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, type)));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
        createSortByVariable(
            sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, VariableType.STRING));
  }

  private void addSortByOutputVariable(
      final SearchSourceBuilder searchRequestBuilder,
      final String sortByField,
      final SortOrder sortOrder) {
    getVariableMultivalueFields()
        .forEach(
            type ->
                searchRequestBuilder.sort(
                    createSortByVariable(
                        sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, type)));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
        createSortByVariable(
            sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, VariableType.STRING));
  }

  private FieldSortBuilder createSortByVariable(
      final String sortByField,
      final SortOrder sortOrder,
      final String prefix,
      final String variablePath,
      final VariableType type) {
    final String inputVariableId = sortByField.substring(prefix.length());
    final String variableValuePath = getVariableValueFieldForType(variablePath, type);
    final String variableIdPath = getVariableClauseIdField(variablePath);

    return SortBuilders.fieldSort(variableValuePath)
        .setNestedSort(
            new NestedSortBuilder(variablePath)
                .setFilter(termQuery(variableIdPath, inputVariableId)))
        .order(sortOrder);
  }
}
