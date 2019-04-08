/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.process.mapping.RawProcessDataResultDtoMapper;
import org.camunda.optimize.service.es.report.result.process.SingleProcessRawDataReportResult;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_VALUE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RawProcessDataCommand extends ProcessReportCommand<SingleProcessRawDataReportResult> {
  private static final String VARIABLE_PREFIX = "variable:";

  private static final Long RAW_DATA_LIMIT = 1_000L;

  private final RawProcessDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawProcessDataResultDtoMapper(RAW_DATA_LIMIT);

  public SingleProcessRawDataReportResult evaluate() {
    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating raw data report for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    final BoolQueryBuilder query = setupBaseQuery(processReportData);

    final String sortByField = processReportData.getParameters().getSorting()
      .flatMap(SortingDto::getBy)
      .orElse(ProcessInstanceType.START_DATE);
    final SortOrder sortOrder = processReportData.getParameters().getSorting()
      .flatMap(SortingDto::getOrder)
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(null, EVENTS)
      .size(RAW_DATA_LIMIT.intValue());

    addSorting(sortByField, sortOrder, searchSourceBuilder);

    final SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final RawDataProcessReportResultDto rawDataProcessReportResultDto = rawDataSingleReportResultDtoMapper.mapFrom(
        response, objectMapper
      );
      return new SingleProcessRawDataReportResult(rawDataProcessReportResultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate raw data report for process definition key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

  }

  @Override
  protected void sortResultData(final SingleProcessRawDataReportResult evaluationResult) {
    // noop, ordering is done on querytime already
  }

  private void addSorting(String sortByField, SortOrder sortOrder, SearchSourceBuilder searchSourceBuilder) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      for (String variableField : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
        searchSourceBuilder.sort(
          SortBuilders
            .fieldSort(variableField + "." + VARIABLE_VALUE)
            .setNestedSort(
              new NestedSortBuilder(variableField)
                .setFilter(termQuery(variableField + "." + VARIABLE_NAME, variableName))
            ).order(sortOrder)
        );
      }
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

}
