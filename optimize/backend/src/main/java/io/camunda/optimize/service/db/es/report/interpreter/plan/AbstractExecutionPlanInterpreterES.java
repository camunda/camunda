/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan;

import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;

public abstract class AbstractExecutionPlanInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    implements ExecutionPlanInterpreter<DATA, PLAN> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractExecutionPlanInterpreterES.class);

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<DATA, PLAN> executionContext) {
    final OptimizeSearchRequestBuilderES searchRequest =
        createBaseQuerySearchRequest(executionContext, getIndexNames(executionContext));
    ResponseBody<?> response;
    try {
      response = executeRequests(executionContext, searchRequest, false);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        if (executionContext.getReportData().getDefinitions().size() > 1) {
          // If there are multiple data sources, we retry with the process instance index multi
          // alias to get a result.
          LOG.info(
              "Could not evaluate report because at least one required instance index {} does not exist. Retrying with index "
                  + "multi alias",
              Arrays.asList(getIndexNames(executionContext)));

          try {
            response =
                executeRequests(
                    executionContext,
                    createBaseQuerySearchRequest(executionContext, getMultiIndexAlias()),
                    true);
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
              "Could not evaluate %s report for definitions [%s]",
              executionContext.getPlan(), executionContext.getReportData().getDefinitions());
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return retrieveQueryResult(response, executionContext);
  }

  protected abstract GroupByInterpreterES<DATA, PLAN> getGroupByInterpreter();

  protected abstract ViewInterpreterES<DATA, PLAN> getViewInterpreter();

  protected abstract OptimizeElasticsearchClient getEsClient();

  protected abstract BoolQuery.Builder getBaseQueryBuilder(
      final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getIndexNames(final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getMultiIndexAlias();

  protected abstract BoolQuery.Builder setupUnfilteredBaseQueryBuilder(
      final ExecutionContext<DATA, PLAN> reportData);

  private OptimizeSearchRequestBuilderES createBaseQuerySearchRequest(
      final ExecutionContext<DATA, PLAN> executionContext, final String... indexes) {
    final Supplier<BoolQuery.Builder> baseQueryBuilderSupplier =
        () -> getBaseQueryBuilder(executionContext);
    final OptimizeSearchRequestBuilderES searchBuilder = new OptimizeSearchRequestBuilderES();
    // The null checks below are essential to prevent NPEs in integration tests
    executionContext
        .getPagination()
        .ifPresent(
            pagination -> {
              Optional.ofNullable(pagination.getOffset()).ifPresent(searchBuilder::from);
              Optional.ofNullable(pagination.getLimit()).ifPresent(searchBuilder::size);
            });

    addAggregation(baseQueryBuilderSupplier.get().build(), searchBuilder, executionContext);

    final BoolQuery.Builder builder = baseQueryBuilderSupplier.get();
    searchBuilder
        .optimizeIndex(getEsClient(), indexes)
        .source(s -> s.fetch(false))
        .trackTotalHits(TrackHits.of(t -> t.enabled(true)));
    getGroupByInterpreter().adjustSearchRequest(searchBuilder, builder, executionContext);
    searchBuilder.query(Query.of(q -> q.bool(builder.build())));
    return searchBuilder;
  }

  private void addAggregation(
      final BoolQuery builder,
      final SearchRequest.Builder searchRequestBuilder,
      final ExecutionContext<DATA, PLAN> executionContext) {
    final Map<String, Aggregation.Builder.ContainerBuilder> aggregations =
        getGroupByInterpreter().createAggregation(builder, executionContext);
    aggregations.forEach((k, v) -> searchRequestBuilder.aggregations(k, v.build()));
  }

  private ResponseBody<?> executeRequests(
      final ExecutionContext<DATA, PLAN> executionContext,
      final OptimizeSearchRequestBuilderES searchRequest,
      final boolean useMultiInstanceIndexAlias)
      throws IOException {

    final String[] indices;
    if (useMultiInstanceIndexAlias) {
      indices = getMultiIndexAlias();
    } else {
      indices = getIndexNames(executionContext);
    }
    final ResponseBody<?> response;
    response = executeElasticSearchCommand(executionContext, searchRequest);
    final BoolQuery.Builder countQueryBuilder = setupUnfilteredBaseQueryBuilder(executionContext);
    executionContext.setUnfilteredTotalInstanceCount(
        getEsClient().count(indices, countQueryBuilder));
    return response;
  }

  private ResponseBody<?> executeElasticSearchCommand(
      final ExecutionContext<DATA, PLAN> executionContext,
      final SearchRequest.Builder searchRequestBuilder)
      throws IOException {
    final ResponseBody<?> response;
    ScrollRequest scrollRequest = null;
    final PaginationDto paginationInfo =
        executionContext.getPagination().orElse(new PaginationDto());
    if (paginationInfo instanceof final PaginationScrollableDto scrollableDto) {
      final String scrollId = scrollableDto.getScrollId();
      final Integer timeout = scrollableDto.getScrollTimeout();
      if (scrollId != null && !scrollId.isEmpty()) {
        scrollRequest =
            ScrollRequest.of(s -> s.scroll(l -> l.time(timeout + "s")).scrollId(scrollId));
      } else {
        searchRequestBuilder.scroll(l -> l.time(timeout + "s"));
      }
      response =
          scrollRequest != null
              ? getEsClient().scroll(scrollRequest, Object.class)
              : getEsClient().search(searchRequestBuilder.build(), Object.class);
    } else {
      response = getEsClient().search(searchRequestBuilder.build());
    }
    return response;
  }

  private CommandEvaluationResult<Object> returnEmptyResult(
      final ExecutionContext<DATA, PLAN> executionContext) {
    LOG.info("Could not evaluate report. Returning empty result instead");
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
      final ResponseBody<?> response, final ExecutionContext<DATA, PLAN> executionContext) {
    final CompositeCommandResult result =
        getGroupByInterpreter().retrieveQueryResult(response, executionContext);
    final CommandEvaluationResult<Object> reportResult =
        ResultInterpreter.interpret(executionContext, result);
    reportResult.setInstanceCount(response.hits().total().value());
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredTotalInstanceCount());
    executionContext
        .getPagination()
        .ifPresent(
            plainPagination -> {
              final PaginationScrollableDto scrollablePagination =
                  PaginationScrollableDto.fromPaginationDto(plainPagination);
              scrollablePagination.setScrollId(response.scrollId());
              reportResult.setPagination(scrollablePagination);
            });
    return reportResult;
  }
}
