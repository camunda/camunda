/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.process.mapping.RawProcessDataResultDtoMapper;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewRawData extends ProcessViewPart {

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;

  private final RawProcessDataResultDtoMapper rawDataSingleReportResultDtoMapper = new RawProcessDataResultDtoMapper();

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    final String sortByField = context.getReportConfiguration().getSorting()
      .flatMap(ReportSortingDto::getBy)
      .orElse(ProcessInstanceIndex.START_DATE);
    final SortOrder sortOrder = context.getReportConfiguration().getSorting()
      .flatMap(ReportSortingDto::getOrder)
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    final SearchSourceBuilder search = searchRequest.source()
      .fetchSource(true)
      .fetchSource(null, new String[]{EVENTS, USER_TASKS});
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
    addSorting(sortByField, sortOrder, searchRequest.source());
  }

  private void addSorting(String sortByField, SortOrder sortOrder, SearchSourceBuilder searchSourceBuilder) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      searchSourceBuilder.sort(
        SortBuilders
          .fieldSort(getNestedVariableValueField())
          .setNestedSort(
            new NestedSortBuilder(VARIABLES)
              .setFilter(termQuery(getNestedVariableNameField(), variableName))
          ).order(sortOrder)
      );
    } else {
      searchSourceBuilder.sort(
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

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return null;
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final List<ProcessInstanceDto> rawDataProcessInstanceDtos;
    if (context.isExport()) {
      rawDataProcessInstanceDtos =
        ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
          response,
          ProcessInstanceDto.class,
          objectMapper,
          esClient,
          configurationService.getEsScrollTimeoutInSeconds(),
          context.getPagination().getLimit()
        );
    } else {
      rawDataProcessInstanceDtos = ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        ProcessInstanceDto.class,
        objectMapper
      );
    }

    final RawDataProcessReportResultDto rawDataSingleReportResultDto = rawDataSingleReportResultDtoMapper.mapFrom(
      rawDataProcessInstanceDtos,
      response.getHits().getTotalHits().value,
      context,
      objectMapper
    );
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawDataSingleReportResultDto);
    return new ViewResult().setProcessRawData(rawDataSingleReportResultDto);
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ViewProperty.RAW_DATA));
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(final ExecutionContext<ProcessReportDataDto> context,
                                                              final RawDataProcessReportResultDto result) {
    final List<String> variableNames = result.getData()
      .stream()
      .flatMap(rawDataProcessInstanceDto -> rawDataProcessInstanceDto.getVariables().keySet().stream())
      .map(varKey -> VARIABLE_PREFIX + varKey)
      .collect(toList());

    TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}
