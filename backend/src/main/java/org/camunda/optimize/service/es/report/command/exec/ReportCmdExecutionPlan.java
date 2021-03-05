/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.util.InstanceIndexUtil.isDecisionInstanceIndexNotFoundException;

@Slf4j
public abstract class ReportCmdExecutionPlan<T, D extends SingleReportDataDto> {

  protected ViewPart<D> viewPart;
  protected GroupByPart<D> groupByPart;
  protected DistributedByPart<D> distributedByPart;
  protected OptimizeElasticsearchClient esClient;
  protected Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

  protected ReportCmdExecutionPlan(final ViewPart<D> viewPart,
                                   final GroupByPart<D> groupByPart,
                                   final DistributedByPart<D> distributedByPart,
                                   final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
                                   final OptimizeElasticsearchClient esClient) {
    groupByPart.setDistributedByPart(distributedByPart);
    distributedByPart.setViewPart(viewPart);
    this.viewPart = viewPart;
    this.groupByPart = groupByPart;
    this.distributedByPart = distributedByPart;
    this.mapToReportResult = mapToReportResult;
    this.esClient = esClient;
  }

  public abstract BoolQueryBuilder setupBaseQuery(final ExecutionContext<D> context);

  protected abstract BoolQueryBuilder setupUnfilteredBaseQuery(final D reportData);

  protected abstract String getIndexName(final ExecutionContext<D> context);

  public <R extends ReportDefinitionDto<D>> CommandEvaluationResult<T> evaluate(final ReportEvaluationContext<R> reportEvaluationContext) {
    return evaluate(new ExecutionContext<>(reportEvaluationContext));
  }

  protected CommandEvaluationResult<T> evaluate(final ExecutionContext<D> executionContext) {
    final D reportData = executionContext.getReportData();

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

  private SearchRequest createBaseQuerySearchRequest(final ExecutionContext<D> executionContext) {
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

  protected abstract Supplier<D> getDataDtoSupplier();

  private CommandEvaluationResult<T> retrieveQueryResult(final SearchResponse response,
                                                         final ExecutionContext<D> executionContext) {
    final CompositeCommandResult result = groupByPart.retrieveQueryResult(response, executionContext);
    result.setViewProperty(viewPart.getViewProperty(executionContext));
    final CommandEvaluationResult<T> reportResult = mapToReportResult.apply(result);
    reportResult.setInstanceCount(response.getHits().getTotalHits().value);
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredInstanceCount());
    Optional.ofNullable(executionContext.getPagination()).ifPresent(reportResult::setPagination);
    return reportResult;
  }

  private void addAggregation(final SearchSourceBuilder searchSourceBuilder,
                              final ExecutionContext<D> executionContext) {
    final List<AggregationBuilder> aggregations = groupByPart.createAggregation(searchSourceBuilder, executionContext);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }

}
