/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec;

import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.GroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.ViewPart;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;

public abstract class ReportCmdExecutionPlan<T, D extends SingleReportDataDto> {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ReportCmdExecutionPlan.class);
  protected ViewPart<D> viewPart;
  protected GroupByPart<D> groupByPart;
  protected DistributedByPart<D> distributedByPart;
  protected DatabaseClient databaseClient;
  private final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

  protected ReportCmdExecutionPlan(
      final ViewPart<D> viewPart,
      final GroupByPart<D> groupByPart,
      final DistributedByPart<D> distributedByPart,
      final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult,
      final DatabaseClient databaseClient) {
    groupByPart.setDistributedByPart(distributedByPart);
    distributedByPart.setViewPart(viewPart);
    this.viewPart = viewPart;
    this.groupByPart = groupByPart;
    this.distributedByPart = distributedByPart;
    this.mapToReportResult = mapToReportResult;
    this.databaseClient = databaseClient;
  }

  public abstract BoolQueryBuilder setupBaseQuery(final ExecutionContext<D> context);

  protected abstract BoolQueryBuilder setupUnfilteredBaseQuery(
      final ExecutionContext<D> reportData);

  protected abstract String[] getIndexNames(final ExecutionContext<D> context);

  protected abstract String[] getMultiIndexAlias();

  public <R extends ReportDefinitionDto<D>> CommandEvaluationResult<T> evaluate(
      final ReportEvaluationContext<R> reportEvaluationContext) {
    return evaluate(new ExecutionContext<>(reportEvaluationContext));
  }

  protected CommandEvaluationResult<T> evaluate(final ExecutionContext<D> executionContext) {
    final SearchRequest searchRequest = createBaseQuerySearchRequest(executionContext);
    SearchResponse response;
    try {
      response = executeRequests(executionContext, searchRequest);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        if (executionContext.getReportData().getDefinitions().size() > 1) {
          // If there are multiple data sources, we retry with the process instance index multi
          // alias to get a result
          log.info(
              "Could not evaluate report because at least one required instance index {} does not exist. Retrying with index "
                  + "multi alias",
              Arrays.asList(getIndexNames(executionContext)));
          searchRequest.indices(getMultiIndexAlias());
          try {
            response = executeRequests(executionContext, searchRequest);
          } catch (final RuntimeException ex) {
            if (isInstanceIndexNotFoundException(e)) {
              return returnEmptyResult(executionContext);
            } else {
              throw ex;
            }
          } catch (final IOException ex) {
            throw e;
          }
        } else {
          return returnEmptyResult(executionContext);
        }
      } else {
        throw e;
      }
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not evaluate %s %s %s report for definitions [%s]",
              viewPart.getClass().getSimpleName(),
              groupByPart.getClass().getSimpleName(),
              distributedByPart.getClass().getSimpleName(),
              executionContext.getReportData().getDefinitions());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return retrieveQueryResult(response, executionContext);
  }

  private CommandEvaluationResult<T> returnEmptyResult(final ExecutionContext<D> executionContext) {
    log.info("Could not evaluate report. Returning empty result instead");
    return mapToReportResult.apply(
        new CompositeCommandResult(
            executionContext.getReportData(),
            viewPart.getViewProperty(executionContext),
            // the default number value differs across views, see the corresponding
            // createEmptyResult implementations
            // thus we refer to it here in order to create an appropriate empty result
            // see https://jira.camunda.com/browse/OPT-3336
            viewPart.createEmptyResult(executionContext).getViewMeasures().stream()
                .findFirst()
                .map(CompositeCommandResult.ViewMeasure::getValue)
                .orElse(null)));
  }

  private SearchResponse executeRequests(
      final ExecutionContext<D> executionContext, final SearchRequest searchRequest)
      throws IOException {
    final SearchResponse response;
    response = executeElasticSearchCommand(executionContext, searchRequest);
    final String[] indices = getIndexNames(executionContext);
    final BoolQueryBuilder countQuery = setupUnfilteredBaseQuery(executionContext);
    executionContext.setUnfilteredTotalInstanceCount(databaseClient.count(indices, countQuery));
    return response;
  }

  private SearchResponse executeElasticSearchCommand(
      final ExecutionContext<D> executionContext, final SearchRequest searchRequest)
      throws IOException {
    final SearchResponse response;
    SearchScrollRequest scrollRequest = null;
    final PaginationDto paginationInfo =
        executionContext.getPagination().orElse(new PaginationDto());
    if (paginationInfo instanceof PaginationScrollableDto) {
      final PaginationScrollableDto scrollableDto = (PaginationScrollableDto) paginationInfo;
      final String scrollId = scrollableDto.getScrollId();
      final Integer timeout = scrollableDto.getScrollTimeout();
      if (scrollId != null && !scrollId.isEmpty()) {
        scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueSeconds(timeout));
      } else {
        searchRequest.scroll(TimeValue.timeValueSeconds(timeout));
      }
      response =
          scrollRequest != null
              ? databaseClient.scroll(scrollRequest)
              : databaseClient.search(searchRequest);
    } else {
      response = databaseClient.search(searchRequest);
    }
    return response;
  }

  private SearchRequest createBaseQuerySearchRequest(final ExecutionContext<D> executionContext) {
    final BoolQueryBuilder baseQuery = setupBaseQuery(executionContext);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(baseQuery).trackTotalHits(true).fetchSource(false);
    // The null checks below are essential to prevent NPEs in integration tests
    executionContext
        .getPagination()
        .ifPresent(
            pagination -> {
              Optional.ofNullable(pagination.getOffset()).ifPresent(searchSourceBuilder::from);
              Optional.ofNullable(pagination.getLimit()).ifPresent(searchSourceBuilder::size);
            });
    addAggregation(searchSourceBuilder, executionContext);

    final SearchRequest searchRequest =
        new SearchRequest(getIndexNames(executionContext)).source(searchSourceBuilder);
    groupByPart.adjustSearchRequest(searchRequest, baseQuery, executionContext);
    return searchRequest;
  }

  public String generateCommandKey() {
    return groupByPart.generateCommandKey(getDataDtoSupplier());
  }

  protected abstract Supplier<D> getDataDtoSupplier();

  private CommandEvaluationResult<T> retrieveQueryResult(
      final SearchResponse response, final ExecutionContext<D> executionContext) {
    final CompositeCommandResult result =
        groupByPart.retrieveQueryResult(response, executionContext);
    final CommandEvaluationResult<T> reportResult = mapToReportResult.apply(result);
    reportResult.setInstanceCount(response.getHits().getTotalHits().value);
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredTotalInstanceCount());
    executionContext
        .getPagination()
        .ifPresent(
            plainPagination -> {
              final PaginationScrollableDto scrollablePagination =
                  PaginationScrollableDto.fromPaginationDto(plainPagination);
              scrollablePagination.setScrollId(response.getScrollId());
              reportResult.setPagination(scrollablePagination);
            });
    return reportResult;
  }

  private void addAggregation(
      final SearchSourceBuilder searchSourceBuilder, final ExecutionContext<D> executionContext) {
    final List<AggregationBuilder> aggregations =
        groupByPart.createAggregation(searchSourceBuilder, executionContext);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }
}
