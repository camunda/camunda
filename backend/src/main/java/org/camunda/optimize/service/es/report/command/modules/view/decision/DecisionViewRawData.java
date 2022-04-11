/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.DecisionVariableReader;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.INPUTS;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
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

  private final DecisionVariableReader decisionVariableReader;
  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDecisionDataResultDtoMapper();

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<DecisionReportDataDto> context) {
    return ViewProperty.RAW_DATA;
  }

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<DecisionReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    final SearchSourceBuilder search = searchRequest.source()
      .fetchSource(true);
    context.getPagination().ifPresent(pag -> {
      if (context.isCsvExport()) {
        search.size(
         pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT ?
            MAX_RESPONSE_SIZE_LIMIT : pag.getLimit());
        searchRequest.scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));
      } else {
        if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
          pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
        }
        search
          .size(pag.getLimit())
          .from(pag.getOffset());
      }
    });

    addSortingToQuery(context.getReportData(), searchRequest.source());
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<DecisionReportDataDto> context) {
    return Collections.emptyList();
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<DecisionReportDataDto> context) {
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos;
    if (context.isCsvExport()) {
      rawDataDecisionInstanceDtos =
        ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
          response,
          DecisionInstanceDto.class,
          objectMapper,
          esClient,
          configurationService.getEsScrollTimeoutInSeconds(),
          context.getPagination().orElse(new PaginationDto()).getLimit()
        );
    } else {
      rawDataDecisionInstanceDtos = ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        DecisionInstanceDto.class,
        objectMapper
      );
    }
    final List<RawDataDecisionInstanceDto> rawData = rawDataSingleReportResultDtoMapper
      .mapFrom(
        rawDataDecisionInstanceDtos,
        getInputVariableEntries(context.getReportData()),
        getOutputVars(context.getReportData())
      );
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  private Set<InputVariableEntry> getInputVariableEntries(final SingleReportDataDto reportDataDto) {
    return decisionVariableReader.getInputVariableNames(
      reportDataDto.getDefinitionKey(),
      reportDataDto.getDefinitionVersions(),
      prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds())
    )
      .stream()
      .map(inputVar -> new InputVariableEntry(inputVar.getId(), inputVar.getName(), inputVar.getType(), null))
      .collect(Collectors.toSet());
  }

  private Set<OutputVariableEntry> getOutputVars(final SingleReportDataDto reportDataDto) {
    return decisionVariableReader.getOutputVariableNames(
      reportDataDto.getDefinitionKey(),
      reportDataDto.getDefinitionVersions(),
      prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds())
    )
      .stream()
      .map(outputVar -> new OutputVariableEntry(
        outputVar.getId(),
        outputVar.getName(),
        outputVar.getType(),
        Collections.emptyList()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<DecisionReportDataDto> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new DecisionViewDto(ViewProperty.RAW_DATA));
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

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(final ExecutionContext<DecisionReportDataDto> context,
                                                              final List<RawDataDecisionInstanceDto> rawData) {
    final List<String> variableNames = rawData
      .stream()
      .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getInputVariables().values().stream())
      .map(this::getPrefixedInputVariableId)
      .collect(toList());
    variableNames.addAll(
      rawData
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
