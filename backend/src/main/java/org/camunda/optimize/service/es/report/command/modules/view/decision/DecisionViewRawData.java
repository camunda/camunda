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
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.es.report.command.decision.mapping.RawDecisionDataResultDtoMapper;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.INPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionViewRawData extends DecisionViewPart {

  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;

  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDecisionDataResultDtoMapper();

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<DecisionReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    final SearchSourceBuilder search = searchRequest.source()
      .fetchSource(true);
    if (context.isExport()) {
      search.size(
        context.getPagination().getLimit() > MAX_RESPONSE_SIZE_LIMIT ?
          MAX_RESPONSE_SIZE_LIMIT : context.getPagination().getLimit());
      searchRequest.scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));
    } else {
      if (context.getPagination().getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
        context.getPagination().setLimit(MAX_RESPONSE_SIZE_LIMIT);
      }
      search
        .size(context.getPagination().getLimit())
        .from(context.getPagination().getOffset());
    }
    addSortingToQuery(context.getReportData(), searchRequest.source());
  }

  private void addSortingToQuery(final DecisionReportDataDto decisionReportData,
                                 final SearchSourceBuilder searchRequestBuilder) {
    final Optional<ReportSortingDto> customSorting = decisionReportData.getConfiguration().getSorting();
    final String sortByField = customSorting.flatMap(ReportSortingDto::getBy)
      .orElse(DecisionInstanceIndex.EVALUATION_DATE_TIME);
    final SortOrder sortOrder = customSorting.flatMap(ReportSortingDto::getOrder)
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
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos;
    if (context.isExport()) {
      rawDataDecisionInstanceDtos =
        ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
          response,
          DecisionInstanceDto.class,
          objectMapper,
          esClient,
          configurationService.getEsScrollTimeoutInSeconds(),
          context.getPagination().getLimit()
        );
    } else {
      rawDataDecisionInstanceDtos = ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        DecisionInstanceDto.class,
        objectMapper
      );
    }

    final RawDataDecisionReportResultDto rawDataSingleReportResultDto = rawDataSingleReportResultDtoMapper.mapFrom(
      rawDataDecisionInstanceDtos, response.getHits().getTotalHits().value, context
    );
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawDataSingleReportResultDto);
    return new ViewResult().setDecisionRawData(rawDataSingleReportResultDto);
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    final DecisionViewDto view = new DecisionViewDto();
    view.setProperty(DecisionViewProperty.RAW_DATA);
    dataForCommandKey.setView(view);
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(final ExecutionContext<DecisionReportDataDto> context,
                                                              final RawDataDecisionReportResultDto result) {
    final List<String> variableNames = result.getData()
      .stream()
      .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getInputVariables().values().stream())
      .map(this::getPrefixedInputVariableId)
      .collect(toList());
    variableNames.addAll(
      result.getData()
        .stream()
        .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getOutputVariables().values().stream())
        .map(this::getPrefixedOutputVariableId)
        .collect(toList())
    );

    TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllDecisionInstanceDtoFieldKeys());
  }

  private String getPrefixedInputVariableId(final InputVariableEntry inputVariableEntry) {
    return INPUT_PREFIX + inputVariableEntry.getId();
  }

  private String getPrefixedOutputVariableId(final OutputVariableEntry outputVariableEntry) {
    return OUTPUT_PREFIX + outputVariableEntry.getId();
  }
}
