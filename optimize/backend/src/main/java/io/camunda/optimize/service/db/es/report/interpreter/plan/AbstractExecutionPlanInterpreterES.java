/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan;

import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.GroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.ViewInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.ExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.interpreter.result.ResultInterpreter;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@Slf4j
public abstract class AbstractExecutionPlanInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    implements ExecutionPlanInterpreter<DATA, PLAN> {

  public CommandEvaluationResult<Object> interpret(ExecutionContext<DATA, PLAN> executionContext) {
    SearchRequest searchRequest = createBaseQuerySearchRequest(executionContext);
    SearchResponse response;
    try {
      response = executeRequests(executionContext, searchRequest, false);
    } catch (RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        if (executionContext.getReportData().getDefinitions().size() > 1) {
          // If there are multiple data sources, we retry with the process instance index multi
          // alias to get a result.
          log.info(
              "Could not evaluate report because at least one required instance index {} does not exist. Retrying with index "
                  + "multi alias",
              Arrays.asList(getIndexNames(executionContext)));

          try {
            response = executeRequests(executionContext, searchRequest, true);
          } catch (RuntimeException ex) {
            if (isInstanceIndexNotFoundException(e)) {
              return returnEmptyResult(executionContext);
            } else {
              throw ex;
            }
          } catch (IOException ex) {
            throw e;
          }
        } else {
          return returnEmptyResult(executionContext);
        }
      } else {
        throw e;
      }
    } catch (IOException e) {
      String reason =
          String.format(
              "Could not evaluate %s report for definitions [%s]",
              executionContext.getPlan(), executionContext.getReportData().getDefinitions());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return retrieveQueryResult(response, executionContext);
  }

  protected abstract GroupByInterpreterES<DATA, PLAN> getGroupByInterpreter();

  protected abstract ViewInterpreterES<DATA, PLAN> getViewInterpreter();

  protected abstract OptimizeElasticsearchClient getEsClient();

  protected abstract BoolQueryBuilder getBaseQuery(final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getIndexNames(final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getMultiIndexAlias();

  protected abstract BoolQueryBuilder setupUnfilteredBaseQuery(
      final ExecutionContext<DATA, PLAN> reportData);

  private SearchRequest createBaseQuerySearchRequest(
      final ExecutionContext<DATA, PLAN> executionContext) {
    final BoolQueryBuilder baseQuery = getBaseQuery(executionContext);
    SearchSourceBuilder searchSourceBuilder =
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

    SearchRequest searchRequest =
        new SearchRequest(getIndexNames(executionContext)).source(searchSourceBuilder);
    getGroupByInterpreter().adjustSearchRequest(searchRequest, baseQuery, executionContext);
    return searchRequest;
  }

  private void addAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<DATA, PLAN> executionContext) {
    final List<AggregationBuilder> aggregations =
        getGroupByInterpreter().createAggregation(searchSourceBuilder, executionContext);
    aggregations.forEach(searchSourceBuilder::aggregation);
  }

  private SearchResponse executeRequests(
      final ExecutionContext<DATA, PLAN> executionContext,
      final SearchRequest searchRequest,
      final boolean useMultiInstanceIndexAlias)
      throws IOException {

    String[] indices;
    if (useMultiInstanceIndexAlias) {
      indices = getMultiIndexAlias();
      searchRequest.indices(indices);
    } else {
      indices = getIndexNames(executionContext);
    }

    SearchResponse response = executeElasticSearchCommand(executionContext, searchRequest);
    BoolQueryBuilder countQuery = setupUnfilteredBaseQuery(executionContext);
    executionContext.setUnfilteredTotalInstanceCount(getEsClient().count(indices, countQuery));
    return response;
  }

  private SearchResponse executeElasticSearchCommand(
      final ExecutionContext<DATA, PLAN> executionContext, final SearchRequest searchRequest)
      throws IOException {
    SearchResponse response;
    SearchScrollRequest scrollRequest = null;
    PaginationDto paginationInfo = executionContext.getPagination().orElse(new PaginationDto());
    if (paginationInfo instanceof PaginationScrollableDto scrollableDto) {
      String scrollId = scrollableDto.getScrollId();
      Integer timeout = scrollableDto.getScrollTimeout();
      if (scrollId != null && !scrollId.isEmpty()) {
        scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueSeconds(timeout));
      } else {
        searchRequest.scroll(TimeValue.timeValueSeconds(timeout));
      }
      response =
          scrollRequest != null
              ? getEsClient().scroll(scrollRequest)
              : getEsClient().search(searchRequest);
    } else {
      response = getEsClient().search(searchRequest);
    }
    return response;
  }

  private CommandEvaluationResult<Object> returnEmptyResult(
      final ExecutionContext<DATA, PLAN> executionContext) {
    log.info("Could not evaluate report. Returning empty result instead");
    return ResultInterpreter.interpret(
        executionContext,
        new CompositeCommandResult(
            executionContext.getReportData(),
            getViewInterpreter().getViewProperty(executionContext),
            // the default number value differs across views, see the corresponding
            // createEmptyResult implementations
            // thus we refer to it here in order to create an appropriate empty result
            // see https://jira.camunda.com/browse/OPT-3336
            getViewInterpreter().createEmptyResult(executionContext).getViewMeasures().stream()
                .findFirst()
                .map(CompositeCommandResult.ViewMeasure::getValue)
                .orElse(null)));
  }

  private CommandEvaluationResult<Object> retrieveQueryResult(
      final SearchResponse response, final ExecutionContext<DATA, PLAN> executionContext) {
    final CompositeCommandResult result =
        getGroupByInterpreter().retrieveQueryResult(response, executionContext);
    final CommandEvaluationResult<Object> reportResult =
        ResultInterpreter.interpret(executionContext, result);
    reportResult.setInstanceCount(response.getHits().getTotalHits().value);
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredTotalInstanceCount());
    executionContext
        .getPagination()
        .ifPresent(
            plainPagination -> {
              PaginationScrollableDto scrollablePagination =
                  PaginationScrollableDto.fromPaginationDto(plainPagination);
              scrollablePagination.setScrollId(response.getScrollId());
              reportResult.setPagination(scrollablePagination);
            });
    return reportResult;
  }
}
