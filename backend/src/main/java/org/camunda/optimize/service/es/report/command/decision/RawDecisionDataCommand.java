/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionParametersDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.es.report.command.decision.mapping.RawDecisionDataResultDtoMapper;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RawDecisionDataCommand extends DecisionReportCommand<SingleDecisionRawDataReportResult> {
  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";

  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDecisionDataResultDtoMapper();

  public SingleDecisionRawDataReportResult evaluate() {
    final DecisionReportDataDto reportData = getReportData();
    logger.debug(
      "Evaluating raw data report for decision definition key [{}] and version [{}]",
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );

    final BoolQueryBuilder query = setupBaseQuery(reportData);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(recordLimit > MAX_RESPONSE_SIZE_LIMIT ? MAX_RESPONSE_SIZE_LIMIT : recordLimit);

    addSortingToQuery(reportData, searchSourceBuilder);

    final SearchRequest scrollSearchRequest = new SearchRequest(DECISION_INSTANCE_TYPE)
      .types(DECISION_INSTANCE_TYPE)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest, RequestOptions.DEFAULT);
      final List<DecisionInstanceDto> rawDataProcessInstanceDtos = ElasticsearchHelper.retrieveScrollResultsTillLimit(
        response,
        DecisionInstanceDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticsearchScrollTimeout(),
        recordLimit
      );

      final RawDataDecisionReportResultDto rawDataDecisionReportResultDto = rawDataSingleReportResultDtoMapper.mapFrom(
        rawDataProcessInstanceDtos, response.getHits().getTotalHits()
      );
      return new SingleDecisionRawDataReportResult(rawDataDecisionReportResultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate raw data report for decision definition key [%s] and version [%s]",
          reportData.getDecisionDefinitionKey(),
          reportData.getDecisionDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  protected void sortResultData(final SingleDecisionRawDataReportResult evaluationResult) {
    // noop, ordering is done on querytime already
  }

  private void addSortingToQuery(final DecisionReportDataDto decisionReportData,
                                 final SearchSourceBuilder searchRequestBuilder) {
    final Optional<SortingDto> customSorting = Optional.ofNullable(decisionReportData.getParameters())
      .flatMap(DecisionParametersDto::getSorting);
    final String sortByField = customSorting.flatMap(SortingDto::getBy)
      .orElse(DecisionInstanceType.EVALUATION_DATE_TIME);
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
    final String variableIdPath = getVariableIdField(variablePath);

    return SortBuilders
      .fieldSort(variableValuePath)
      .setNestedSort(
        new NestedSortBuilder(variablePath)
          .setFilter(termQuery(variableIdPath, inputVariableId))
      )
      .order(sortOrder);
  }

}
