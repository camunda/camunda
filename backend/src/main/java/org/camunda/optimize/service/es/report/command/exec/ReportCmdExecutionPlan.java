/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Slf4j
public class ReportCmdExecutionPlan<R extends SingleReportResultDto> {

  private GroupByPart groupByPart;
  private DistributedByPart distributedByPart;
  private ViewPart viewPart;
  private OptimizeElasticsearchClient esClient;
  private ProcessDefinitionReader processDefinitionReader;
  private ProcessQueryFilterEnhancer queryFilterEnhancer;
  private Function<CompositeCommandResult, R> mapToReportResult;

  public ReportCmdExecutionPlan(final ViewPart viewPart,
                                final GroupByPart groupByPart,
                                final DistributedByPart distributedByPart,
                                final Function<CompositeCommandResult, R> mapToReportResult,
                                final OptimizeElasticsearchClient esClient,
                                final ProcessDefinitionReader processDefinitionReader,
                                final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    groupByPart.setDistributedByPart(distributedByPart);
    distributedByPart.setViewPart(viewPart);
    this.viewPart = viewPart;
    this.groupByPart = groupByPart;
    this.distributedByPart = distributedByPart;
    this.mapToReportResult = mapToReportResult;
    this.esClient = esClient;
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportData) {
    BoolQueryBuilder boolQueryBuilder = createDefinitionQuery(
      reportData.getDefinitionKey(),
      reportData.getDefinitionVersions(),
      reportData.getTenantIds(),
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
    queryFilterEnhancer.addFilterToQuery(boolQueryBuilder, reportData.getFilter());
    return boolQueryBuilder;
  }

  public R evaluate(final ProcessReportDataDto definitionData) {

    final BoolQueryBuilder baseQuery = setupBaseQuery(definitionData);
    groupByPart.adjustBaseQuery(baseQuery, definitionData);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .fetchSource(false)
      .size(0);
    addAggregation(searchSourceBuilder, definitionData);

    SearchRequest searchRequest = new SearchRequest(getIndexName())
      .types(getIndexName())
      .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate %s %s %s report " +
            "for process definition with key [%s] and versions [%s]",
          viewPart.getClass().getSimpleName(),
          groupByPart.getClass().getSimpleName(),
          distributedByPart.getClass().getSimpleName(),
          definitionData.getProcessDefinitionKey(),
          definitionData.getProcessDefinitionVersions()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return retrieveQueryResult(response, definitionData);
  }

  public String generateCommandKey() {
    return groupByPart.generateCommandKey();
  }

  private R retrieveQueryResult(final SearchResponse response, final ProcessReportDataDto definitionData) {
    final CompositeCommandResult result = groupByPart.retrieveQueryResult(response, definitionData);
    final R reportResult = mapToReportResult.apply(result);
    reportResult.setInstanceCount(response.getHits().getTotalHits());
    final Optional<SortingDto> sorting = groupByPart.getSorting(definitionData);
    sorting.ifPresent(
      sortingDto -> reportResult.sortResultData(sortingDto, groupByPart.getSortByKeyIsOfNumericType(definitionData))
    );
    return reportResult;
  }

  private void addAggregation(final SearchSourceBuilder searchSourceBuilder,
                              final ProcessReportDataDto definitionData) {
    final List<AggregationBuilder> aggregations = groupByPart.createAggregation(searchSourceBuilder, definitionData);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }

  private String getIndexName() {
    return PROCESS_INSTANCE_INDEX_NAME;
  }

}
