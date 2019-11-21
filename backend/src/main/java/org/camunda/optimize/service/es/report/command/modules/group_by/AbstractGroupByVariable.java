/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;

@RequiredArgsConstructor
public abstract class AbstractGroupByVariable<Data extends SingleReportDataDto> extends GroupByPart<Data> {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  private static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "inst_count";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";

  private static final String STATS = "stats";
  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private final ConfigurationService configurationService;
  private final IntervalAggregationService intervalAggregationService;
  private final OptimizeElasticsearchClient esClient;

  protected abstract String getVariableName(final ExecutionContext<Data> context);

  protected abstract VariableType getVariableType(final ExecutionContext<Data> context);

  protected abstract String getNestedVariableNameFieldLabel();

  protected abstract String getNestedVariableTypeField();

  protected abstract String getNestedVariableValueFieldLabel(final VariableType type);

  protected abstract String getVariablePath();

  protected abstract String getIndexName();

  protected abstract BoolQueryBuilder getVariableUndefinedOrNullQuery(final ExecutionContext<Data> context);

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<Data> context) {
    AggregationBuilder variableSubAggregation =
      createVariableSubAggregation(context, searchSourceBuilder.query());

    final NestedAggregationBuilder variableAggregation = nested(NESTED_AGGREGATION, getVariablePath())
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getNestedVariableNameFieldLabel(), getVariableName(context)))
            .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
            .must(existsQuery(getNestedVariableValueFieldLabel(VariableType.STRING)))
        )
          .subAggregation(variableSubAggregation)
          .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION))
      );
    final AggregationBuilder undefinedOrNullVariableAggregation =
      createUndefinedOrNullVariableAggregation(context);
    return Arrays.asList(variableAggregation, undefinedOrNullVariableAggregation);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<Data> context) {
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      getVariableUndefinedOrNullQuery(context)
    )
      .subAggregation(distributedByPart.createAggregation(context));
  }

  private AggregationBuilder createVariableSubAggregation(final ExecutionContext<Data> context,
                                                          final QueryBuilder baseQuery) {
    AggregationBuilder aggregationBuilder = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getNestedVariableValueFieldLabel(VariableType.STRING));

    if (VariableType.DATE.equals(getVariableType(context))) {
      aggregationBuilder =
        createDateVariableAggregation(baseQuery, context);
    }

    AggregationBuilder operationsAggregation = reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(distributedByPart.createAggregation(context));


    aggregationBuilder.subAggregation(operationsAggregation);
    return aggregationBuilder;
  }

  private AggregationBuilder createDateVariableAggregation(final QueryBuilder baseQuery,
                                                           final ExecutionContext<Data> context) {
    Stats minMaxStats = getMinMaxStats(baseQuery, context);

    Optional<AggregationBuilder> optionalAgg = intervalAggregationService
      .createIntervalAggregationFromGivenRange(
        getNestedVariableValueFieldLabel(VariableType.DATE),
        OffsetDateTime.parse(minMaxStats.getMinAsString(), dateTimeFormatter),
        OffsetDateTime.parse(minMaxStats.getMaxAsString(), dateTimeFormatter)
      );
    AggregationBuilder aggregationBuilder;
    if (optionalAgg.isPresent() && !((RangeAggregationBuilder) optionalAgg.get()).ranges().isEmpty()) {
      aggregationBuilder = optionalAgg.get();
    } else {
      aggregationBuilder = AggregationBuilders
        .dateHistogram(AbstractGroupByVariable.VARIABLES_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(VariableType.DATE))
        .interval(1)
        .format(OPTIMIZE_DATE_FORMAT)
        .timeZone(DateTimeZone.getDefault());
    }
    return aggregationBuilder;
  }

  private Stats getMinMaxStats(final QueryBuilder query,
                               final ExecutionContext<Data> context) {

    AggregationBuilder aggregationBuilder = nested(NESTED_AGGREGATION, getVariablePath()).subAggregation(filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(
          termQuery(getNestedVariableNameFieldLabel(), getVariableName(context))
        )
    ));

    AggregationBuilder statsAgg = aggregationBuilder
      .subAggregation(
        stats(STATS)
          .field(getNestedVariableValueFieldLabel(VariableType.DATE))
          .format(OPTIMIZE_DATE_FORMAT)
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAgg)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(getIndexName()).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not automatically determine date interval", e);
    }
    return ((Nested) response.getAggregations().get(NESTED_AGGREGATION)).getAggregations().get(STATS);
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<Data> context) {
    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      ReverseNested reverseNested = b.getAggregations().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
      final List<DistributedByResult> distribution =
        distributedByPart.retrieveResult(response, reverseNested.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(b.getKeyAsString(), distribution));
    }

    final ReverseNested filteredInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredInstAggr.getDocCount();

    if (response.getHits().getTotalHits() > filteredProcInstCount) {

      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(response, aggregation.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(isResultComplete(variableTerms));
    if (VariableType.DATE.equals(getVariableType(context))) {
      compositeCommandResult.setSorting(new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC));
    }
    compositeCommandResult.setKeyIsOfNumericType(getSortByKeyIsOfNumericType(context));
  }

  private boolean getSortByKeyIsOfNumericType(final ExecutionContext<Data> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }
}
