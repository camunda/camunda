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
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
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
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;

@Slf4j
public abstract class ReportCmdExecutionPlan<T, D extends SingleReportDataDto> {

  protected ViewPart<D> viewPart;
  protected GroupByPart<D> groupByPart;
  protected DistributedByPart<D> distributedByPart;
  protected OptimizeElasticsearchClient esClient;
  private Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

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

  protected abstract String[] getIndexNames(final ExecutionContext<D> context);

  public <R extends ReportDefinitionDto<D>> CommandEvaluationResult<T> evaluate(final ReportEvaluationContext<R> reportEvaluationContext) {
    return evaluate(new ExecutionContext<>(reportEvaluationContext));
  }

  protected CommandEvaluationResult<T> evaluate(final ExecutionContext<D> executionContext) {
    final D reportData = executionContext.getReportData();

    SearchRequest searchRequest = createBaseQuerySearchRequest(executionContext);
    CountRequest unfilteredInstanceCountRequest =
      new CountRequest(getIndexNames(executionContext)).query(setupUnfilteredBaseQuery(reportData));

    SearchResponse response;
    CountResponse unfilteredInstanceCountResponse;
    try {
      response = executeElasticSearchCommand(executionContext, searchRequest);
      unfilteredInstanceCountResponse = esClient.count(unfilteredInstanceCountRequest);
      executionContext.setUnfilteredInstanceCount(unfilteredInstanceCountResponse.getCount());
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate %s %s %s report for definitions [%s]",
          viewPart.getClass().getSimpleName(),
          groupByPart.getClass().getSimpleName(),
          distributedByPart.getClass().getSimpleName(),
          reportData.getDefinitions()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        log.info(
          "Could not evaluate report because required instance index {} does not exist. Returning empty result instead",
          Arrays.asList(getIndexNames(executionContext))
        );
        return mapToReportResult.apply(new CompositeCommandResult(
          executionContext.getReportData(),
          viewPart.getViewProperty(executionContext),
          // the default number value differs across views, see the corresponding createEmptyResult implementations
          // thus we refer to it here in order to create an appropriate empty result
          // see https://jira.camunda.com/browse/OPT-3336
          viewPart.createEmptyResult(executionContext).getViewMeasures().stream()
            .findFirst()
            .map(CompositeCommandResult.ViewMeasure::getValue)
            .orElse(null)
        ));
      } else {
        throw e;
      }
    }
    return retrieveQueryResult(response, executionContext);
  }

  private SearchResponse executeElasticSearchCommand(final ExecutionContext<D> executionContext,
                                                     final SearchRequest searchRequest) throws IOException {
    SearchResponse response;
    SearchScrollRequest scrollRequest = null;
    PaginationDto paginationInfo = executionContext.getPagination().orElse(new PaginationDto());
    if (paginationInfo instanceof PaginationScrollableDto) {
      PaginationScrollableDto scrollableDto = (PaginationScrollableDto) paginationInfo;
      String scrollId = scrollableDto.getScrollId();
      Integer timeout = scrollableDto.getScrollTimeout();
      if (scrollId != null && !scrollId.isEmpty()) {
        scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueSeconds(timeout));
      } else {
        searchRequest.scroll(TimeValue.timeValueSeconds(timeout));
      }
      response = (scrollRequest != null ? esClient.scroll(scrollRequest) : esClient.search(searchRequest));
    } else {
      response = esClient.search(searchRequest);
    }
    return response;
  }

  private SearchRequest createBaseQuerySearchRequest(final ExecutionContext<D> executionContext) {
    final BoolQueryBuilder baseQuery = setupBaseQuery(executionContext);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .trackTotalHits(true)
      .fetchSource(false);
    // The null checks below are essential, otherwise over 4000 tests will fail because of null pointer exceptions
    executionContext.getPagination().ifPresent(
      pagination -> {
        Optional.ofNullable(pagination.getOffset()).ifPresent(
          searchSourceBuilder::from);
        Optional.ofNullable(pagination.getLimit()).ifPresent(
          searchSourceBuilder::size);
      });
    addAggregation(searchSourceBuilder, executionContext);

    SearchRequest searchRequest = new SearchRequest(getIndexNames(executionContext)).source(searchSourceBuilder);
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
    final CommandEvaluationResult<T> reportResult = mapToReportResult.apply(result);
    reportResult.setInstanceCount(response.getHits().getTotalHits().value);
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredInstanceCount());
    executionContext.getPagination().ifPresent(
      plainPagination -> {
        PaginationScrollableDto scrollablePagination = PaginationScrollableDto.fromPaginationDto(plainPagination);
        scrollablePagination.setScrollId(response.getScrollId());
        reportResult.setPagination(scrollablePagination);
      });
    return reportResult;
  }

  private void addAggregation(final SearchSourceBuilder searchSourceBuilder,
                              final ExecutionContext<D> executionContext) {
    final List<AggregationBuilder> aggregations = groupByPart.createAggregation(searchSourceBuilder, executionContext);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }

}
