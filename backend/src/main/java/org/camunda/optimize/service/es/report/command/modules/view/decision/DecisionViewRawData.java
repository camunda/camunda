/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.es.report.command.decision.mapping.RawDecisionDataResultDtoMapper;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.INPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionViewRawData extends DecisionViewPart {

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;

  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";

  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDecisionDataResultDtoMapper();

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<DecisionReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    searchRequest.source()
      .fetchSource(true)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(context.getRecordLimit() > MAX_RESPONSE_SIZE_LIMIT ? MAX_RESPONSE_SIZE_LIMIT : context.getRecordLimit());

    addSortingToQuery(context.getReportData(), searchRequest.source());

    searchRequest
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));
  }

  private void addSortingToQuery(final DecisionReportDataDto decisionReportData,
                                 final SearchSourceBuilder searchRequestBuilder) {
    final Optional<SortingDto> customSorting = decisionReportData.getConfiguration().getSorting();
    final String sortByField = customSorting.flatMap(SortingDto::getBy)
      .orElse(DecisionInstanceIndex.EVALUATION_DATE_TIME);
    final SortOrder sortOrder = customSorting.flatMap(SortingDto::getOrder)
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    if (sortByField.startsWith(INPUT_VARIABLE_PREFIX)) {
      addSortByInputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else if (sortByField.startsWith(OUTPUT_VARIABLE_PREFIX)) {
      addSortByOutputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else {
      searchRequestBuilder.sort(
        SortBuilders.fieldSort(sortByField).order(sortOrder)
          // this ensures the query doesn't fail on unknown properties but just ignores them
          // this is done to ensure consistent behavior compared to unknown variable names as ES doesn't fail there
          // @formatter:off
          // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
          // @formatter:on
          .unmappedType("short")
      );
    }
  }

  private void addSortByInputVariable(final SearchSourceBuilder searchRequestBuilder,
                                      final String sortByField,
                                      final SortOrder sortOrder) {
    getVariableMultivalueFields()
      .forEach(type -> searchRequestBuilder.sort(
        createSortByVariable(sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, type)
      ));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
      createSortByVariable(sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, VariableType.STRING)
    );
  }

  private void addSortByOutputVariable(final SearchSourceBuilder searchRequestBuilder,
                                       final String sortByField,
                                       final SortOrder sortOrder) {
    getVariableMultivalueFields()
      .forEach(type -> searchRequestBuilder.sort(
        createSortByVariable(sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, type)
      ));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
      createSortByVariable(sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, VariableType.STRING)
    );
  }

  private FieldSortBuilder createSortByVariable(final String sortByField,
                                                final SortOrder sortOrder,
                                                final String prefix,
                                                final String variablePath,
                                                final VariableType type) {
    final String inputVariableId = sortByField.substring(prefix.length());
    final String variableValuePath = getVariableValueFieldForType(variablePath, type);
    final String variableIdPath = getVariableClauseIdField(variablePath);

    return SortBuilders
      .fieldSort(variableValuePath)
      .setNestedSort(
        new NestedSortBuilder(variablePath)
          .setFilter(termQuery(variableIdPath, inputVariableId))
      )
      .order(sortOrder);
  }


  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<DecisionReportDataDto> context) {
    return null;
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<DecisionReportDataDto> context) {
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos =
      ElasticsearchHelper.retrieveScrollResultsTillLimit(
        response,
        DecisionInstanceDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticsearchScrollTimeout(),
        context.getRecordLimit()
      );

    final RawDataDecisionReportResultDto rawDataSingleReportResultDto = rawDataSingleReportResultDtoMapper.mapFrom(
      rawDataDecisionInstanceDtos, response.getHits().getTotalHits()
    );
    return new ViewResult().setDecisionRawData(rawDataSingleReportResultDto);
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    final DecisionViewDto view = new DecisionViewDto();
    view.setProperty(DecisionViewProperty.RAW_DATA);
    dataForCommandKey.setView(view);
  }
}
