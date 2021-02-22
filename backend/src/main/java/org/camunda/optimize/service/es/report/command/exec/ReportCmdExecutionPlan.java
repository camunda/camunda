/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.util.InstanceIndexUtil.isDecisionInstanceIndexNotFoundException;

@Slf4j
public abstract class ReportCmdExecutionPlan<R extends SingleReportResultDto, Data extends SingleReportDataDto> {

  protected ViewPart<Data> viewPart;
  protected GroupByPart<Data> groupByPart;
  protected DistributedByPart<Data> distributedByPart;
  protected OptimizeElasticsearchClient esClient;
  protected Function<CompositeCommandResult, R> mapToReportResult;

  public ReportCmdExecutionPlan(final ViewPart<Data> viewPart,
                                final GroupByPart<Data> groupByPart,
                                final DistributedByPart<Data> distributedByPart,
                                final Function<CompositeCommandResult, R> mapToReportResult,
                                final OptimizeElasticsearchClient esClient) {
    groupByPart.setDistributedByPart(distributedByPart);
    distributedByPart.setViewPart(viewPart);
    this.viewPart = viewPart;
    this.groupByPart = groupByPart;
    this.distributedByPart = distributedByPart;
    this.mapToReportResult = mapToReportResult;
    this.esClient = esClient;
  }

  public abstract BoolQueryBuilder setupBaseQuery(final ExecutionContext<Data> context);

  protected abstract BoolQueryBuilder setupUnfilteredBaseQuery(final Data reportData);

  protected abstract String getIndexName(final ExecutionContext<Data> context);

  public <T extends ReportDefinitionDto<Data>> R evaluate(final CommandContext<T> commandContext) {
    return evaluate(new ExecutionContext<>(commandContext));
  }

  protected R evaluate(final ExecutionContext<Data> executionContext) {
    final Data reportData = executionContext.getReportData();

    SearchRequest searchRequest = createBaseQuerySearchRequest(executionContext);
    CountRequest unfilteredInstanceCountRequest =
      new CountRequest(getIndexName(executionContext)).query(setupUnfilteredBaseQuery(reportData));

    SearchResponse response;
    CountResponse unfilteredInstanceCountResponse;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      unfilteredInstanceCountResponse = esClient.count(unfilteredInstanceCountRequest, RequestOptions.DEFAULT);
      executionContext.setUnfilteredInstanceCount(unfilteredInstanceCountResponse.getCount());
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate %s %s %s report for definition with key [%s] and versions [%s]",
          viewPart.getClass().getSimpleName(),
          groupByPart.getClass().getSimpleName(),
          distributedByPart.getClass().getSimpleName(),
          reportData.getDefinitionKey(),
          reportData.getDefinitionVersions()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isDecisionInstanceIndexNotFoundException(e)) {
        log.warn(
          "Could not evaluate report. Required instance index does not exist, no instances have been imported yet" +
            " for the specified definition. Returning empty result instead",
          e
        );
        return mapToReportResult.apply(new CompositeCommandResult(executionContext.getReportData()));
      } else {
        throw e;
      }
    }

    return retrieveQueryResult(response, executionContext);
  }

  private SearchRequest createBaseQuerySearchRequest(final ExecutionContext<Data> executionContext) {
    final BoolQueryBuilder baseQuery = setupBaseQuery(executionContext);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .trackTotalHits(true)
      .fetchSource(false)
      .size(0);
    addAggregation(searchSourceBuilder, executionContext);

    SearchRequest searchRequest = new SearchRequest(getIndexName(executionContext)).source(searchSourceBuilder);
    groupByPart.adjustSearchRequest(searchRequest, baseQuery, executionContext);
    return searchRequest;
  }

  public String generateCommandKey() {
    return groupByPart.generateCommandKey(getDataDtoSupplier());
  }

  protected abstract Supplier<Data> getDataDtoSupplier();

  private R retrieveQueryResult(final SearchResponse response, final ExecutionContext<Data> executionContext) {
    final CompositeCommandResult result = groupByPart.retrieveQueryResult(response, executionContext);
    final R reportResult = mapToReportResult.apply(result);
    reportResult.setInstanceCount(response.getHits().getTotalHits().value);
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredInstanceCount());
    return reportResult;
  }

  private void addAggregation(final SearchSourceBuilder searchSourceBuilder,
                              final ExecutionContext<Data> executionContext) {
    final List<AggregationBuilder> aggregations = groupByPart.createAggregation(searchSourceBuilder, executionContext);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }

}
