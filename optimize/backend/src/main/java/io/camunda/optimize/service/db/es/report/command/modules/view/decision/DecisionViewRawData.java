/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.decision;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.OUTPUTS;
import static io.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.report.command.decision.mapping.RawDecisionDataResultDtoMapper;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionViewRawData extends DecisionViewPart {

  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DecisionViewRawData.class);

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DatabaseClient databaseClient;

  private final DecisionVariableReader decisionVariableReader;
  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
      new RawDecisionDataResultDtoMapper();

  public DecisionViewRawData(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient,
      final DecisionVariableReader decisionVariableReader) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.databaseClient = databaseClient;
    this.decisionVariableReader = decisionVariableReader;
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<DecisionReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

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
  public ViewProperty getViewProperty(final ExecutionContext<DecisionReportDataDto> context) {
    return ViewProperty.RAW_DATA;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<DecisionReportDataDto> context) {
    return Collections.emptyList();
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<DecisionReportDataDto> context) {
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos;
    if (context.isCsvExport()) {
      rawDataDecisionInstanceDtos =
          ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
              response,
              DecisionInstanceDto.class,
              objectMapper,
              databaseClient,
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

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new DecisionViewDto(ViewProperty.RAW_DATA));
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<DecisionReportDataDto> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  private Set<InputVariableEntry> getInputVariableEntries(final SingleReportDataDto reportDataDto) {
    return decisionVariableReader
        .getInputVariableNames(
            reportDataDto.getDefinitionKey(),
            reportDataDto.getDefinitionVersions(),
            prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds()))
        .stream()
        .map(
            inputVar ->
                new InputVariableEntry(
                    inputVar.getId(), inputVar.getName(), inputVar.getType(), null))
        .collect(Collectors.toSet());
  }

  private Set<OutputVariableEntry> getOutputVars(final SingleReportDataDto reportDataDto) {
    return decisionVariableReader
        .getOutputVariableNames(
            reportDataDto.getDefinitionKey(),
            reportDataDto.getDefinitionVersions(),
            prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds()))
        .stream()
        .map(
            outputVar ->
                new OutputVariableEntry(
                    outputVar.getId(),
                    outputVar.getName(),
                    outputVar.getType(),
                    Collections.emptyList()))
        .collect(Collectors.toSet());
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
              // @formatter:off
              // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
              // @formatter:on
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

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<DecisionReportDataDto> context,
      final List<RawDataDecisionInstanceDto> rawData) {
    final List<String> variableNames =
        rawData.stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getInputVariables().values().stream())
            .map(this::getPrefixedInputVariableId)
            .collect(toList());
    variableNames.addAll(
        rawData.stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getOutputVariables().values().stream())
            .map(this::getPrefixedOutputVariableId)
            .collect(toList()));

    final TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
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
