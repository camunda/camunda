/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
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
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
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
  public Optional<MinMaxStatDto> calculateNumberRangeForGroupByNumberVariable(final ExecutionContext<Data> context,
                                                                              final BoolQueryBuilder baseQuery) {
    if (isGroupedByNumberVariable(getVariableType(context))) {
      return Optional.of(getMinMaxStats(baseQuery, context));
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<Data> context) {
    Optional<AggregationBuilder> variableSubAggregation =
      createVariableSubAggregation(context, searchSourceBuilder.query());

    if (!variableSubAggregation.isPresent()) {
      // if the report contains no instances and is grouped by date variable, this agg will not be present
      // as it is based on instance data
      return Collections.emptyList();
    }

    final NestedAggregationBuilder variableAggregation = nested(NESTED_AGGREGATION, getVariablePath())
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getNestedVariableNameFieldLabel(), getVariableName(context)))
            .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
            .must(existsQuery(getNestedVariableValueFieldLabel(VariableType.STRING)))
        )
          .subAggregation(variableSubAggregation.get())
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

  private Optional<AggregationBuilder> createVariableSubAggregation(final ExecutionContext<Data> context,
                                                                    final QueryBuilder baseQuery) {
    AggregationBuilder aggregationBuilder = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getNestedVariableValueFieldLabel(VariableType.STRING));

    if (VariableType.DATE.equals(getVariableType(context))) {
      Optional<AggregationBuilder> dateVariableAggregation = createDateVariableAggregation(baseQuery, context);
      if (!dateVariableAggregation.isPresent()) {
        return Optional.empty();
      }
      aggregationBuilder = dateVariableAggregation.get();
    } else if (isGroupedByNumberVariable(getVariableType(context))) {
      Optional<AggregationBuilder> numberVariableAggregation = createNumberVariableAggregation(baseQuery, context);
      if (!numberVariableAggregation.isPresent()) {
        return Optional.empty();
      }
      aggregationBuilder = numberVariableAggregation.get();
    }

    AggregationBuilder operationsAggregation = reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(distributedByPart.createAggregation(context));


    aggregationBuilder.subAggregation(operationsAggregation);
    return Optional.of(aggregationBuilder);
  }

  private Optional<AggregationBuilder> createNumberVariableAggregation(final QueryBuilder baseQuery,
                                                                       final ExecutionContext<Data> context) {
    final MinMaxStatDto minMaxStats = getMinMaxStats(baseQuery, context);
    if (!minMaxStats.isMinValid()) {
      return Optional.empty();
    }

    final Optional<Double> min = getBaselineForNumberVariableAggregation(context, minMaxStats);
    if (!min.isPresent()) {
      // no valid baseline is set, return empty result
      return Optional.empty();
    }

    final double unit = getGroupByNumberVariableUnit(context, min.get(), minMaxStats);
    final double max = getMaxForNumberVariableAggregation(context, minMaxStats);
    int numberOfBuckets = 0;

    RangeAggregationBuilder rangeAgg = AggregationBuilders
      .range(RANGE_AGGREGATION)
      .field(getNestedVariableValueFieldLabel(getVariableType(context)));

    for (double start = min.get();
         start <= max && numberOfBuckets < configurationService.getEsAggregationBucketLimit();
         start += unit) {
      RangeAggregator.Range range =
        new RangeAggregator.Range(
          getKeyForNumberBucket(getVariableType(context), start),
          start,
          start + unit
        );
      rangeAgg.addRange(range);
      numberOfBuckets++;
    }
    return Optional.of(rangeAgg);
  }

  private Optional<AggregationBuilder> createDateVariableAggregation(final QueryBuilder baseQuery,
                                                                     final ExecutionContext<Data> context) {
    GroupByDateUnit unit = getGroupByDateUnit(context);
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      MinMaxStatDto minMaxStats = getMinMaxStats(baseQuery, context);
      if (!minMaxStats.isMinValid()) {
        return Optional.empty();
      }
      if (minMaxStats.isValidRange()) {
        return Optional.of(createAutomaticIntervalAggregation(minMaxStats, context));
      }
      // if there is only one instance we always group by month
      unit = GroupByDateUnit.MONTH;
    }

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);

    return Optional.of(
      AggregationBuilders
        .dateHistogram(AbstractGroupByVariable.VARIABLES_AGGREGATION)
        .field(getNestedVariableValueFieldLabel(VariableType.DATE))
        .dateHistogramInterval(interval)
        .format(OPTIMIZE_DATE_FORMAT)
        .timeZone(ZoneId.systemDefault()));
  }

  private AggregationBuilder createAutomaticIntervalAggregation(final MinMaxStatDto minMaxStats,
                                                                final ExecutionContext<Data> context) {
    OffsetDateTime minDateTime =
      OffsetDateTime.ofInstant(Instant.ofEpochMilli(Math.round(minMaxStats.getMin())), ZoneId.systemDefault());
    OffsetDateTime maxDateTime =
      OffsetDateTime.ofInstant(Instant.ofEpochMilli(Math.round(minMaxStats.getMax())), ZoneId.systemDefault());
    AggregationBuilder automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregationFromGivenRange(
        getNestedVariableValueFieldLabel(VariableType.DATE),
        minDateTime,
        maxDateTime
      );

    return automaticIntervalAggregation.subAggregation(distributedByPart.createAggregation(context));
  }

  protected MinMaxStatDto getMinMaxStats(final QueryBuilder baseQuery,
                                         final ExecutionContext<Data> context) {
    AggregationBuilder statsAggregation = VariableType.DATE.equals(getVariableType(context))
      ? stats(STATS).field(getNestedVariableValueFieldLabel(VariableType.DATE)).format(OPTIMIZE_DATE_FORMAT)
      : stats(STATS).field(getNestedVariableValueFieldLabel(getVariableType(context)));

    AggregationBuilder filterAggregation = filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(
          termQuery(getNestedVariableNameFieldLabel(), getVariableName(context))
        )
    ).subAggregation(statsAggregation);

    AggregationBuilder aggregationBuilder =
      nested(NESTED_AGGREGATION, getVariablePath())
        .subAggregation(filterAggregation);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .fetchSource(false)
      .aggregation(aggregationBuilder)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(getIndexName()).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not automatically determine date interval", e);
    }

    final ParsedNested nestedAgg = response.getAggregations().get(NESTED_AGGREGATION);
    final ParsedFilter filterAgg = nestedAgg.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    final ParsedStats stats = filterAgg.getAggregations().get(STATS);
    return new MinMaxStatDto(
      stats.getMin(),
      stats.getMax(),
      stats.getMinAsString(),
      stats.getMaxAsString()
    );
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<Data> context) {
    if (response.getAggregations() == null) {
      // aggregations are null if there are no instances in the report and it is grouped by date variable
      return;
    }
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

    if (response.getHits().getTotalHits().value > filteredProcInstCount) {

      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(response, aggregation.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(isResultComplete(filteredVariables, variableTerms));
    if (VariableType.DATE.equals(getVariableType(context))) {
      compositeCommandResult.setSorting(new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC));
    }
    compositeCommandResult.setKeyIsOfNumericType(getSortByKeyIsOfNumericType(context));
  }

  private boolean getSortByKeyIsOfNumericType(final ExecutionContext<Data> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  private boolean isResultComplete(final Filter filteredVariables,
                                   final MultiBucketsAggregation variableTerms) {
    final long resultDocCount = variableTerms.getBuckets()
      .stream()
      .mapToLong(MultiBucketsAggregation.Bucket::getDocCount)
      .sum();
    return filteredVariables.getDocCount() == resultDocCount;
  }

  private Double getMaxForNumberVariableAggregation(final ExecutionContext<Data> context,
                                                    final MinMaxStatDto minMaxStats) {
    return context.getNumberVariableRange().isPresent()
      ? context.getNumberVariableRange().get().getMaximum()
      : minMaxStats.getMax();
  }

  private Optional<Double> getBaselineForNumberVariableAggregation(final ExecutionContext<Data> context,
                                                                   final MinMaxStatDto minMaxStats) {
    final Optional<Range<Double>> range = context.getNumberVariableRange();
    final Optional<Double> baselineForSingleReport = context.getReportData()
      .getConfiguration()
      .getBaselineForNumberVariableReport();

    if (!range.isPresent()
      && baselineForSingleReport.isPresent()) {
      if (baselineForSingleReport.get() > minMaxStats.getMax()) {
        // if report is single report and invalid baseline is set, return empty result
        return Optional.empty();
      }
      // if report is single report and a valid baseline is set, use this instead of the min. range value
      return baselineForSingleReport;
    }

    return range.isPresent()
      ? Optional.of(roundBaselineToNearestPowerOfTen(range.get().getMinimum()))
      : Optional.of(roundBaselineToNearestPowerOfTen(minMaxStats.getMin()));
  }

  private Double getGroupByNumberVariableUnit(final ExecutionContext<Data> context,
                                              final Double baseline,
                                              final MinMaxStatDto minMaxStats) {
    final Optional<Range<Double>> rangeForCombinedReport = context.getNumberVariableRange();
    final double maxVariableValue = rangeForCombinedReport.isPresent()
      ? rangeForCombinedReport.get().getMaximum()
      : minMaxStats.getMax();

    final boolean customBucketsActive = context.getReportData().getConfiguration().getCustomNumberBucket().isActive();
    Double unit = context.getReportData().getConfiguration().getCustomNumberBucket().getBucketSize();
    if (!customBucketsActive || unit == null || unit <= 0) {
      // if no valid unit is configured, calculate default automatic unit
      unit =
        (maxVariableValue - baseline)
          / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1); // -1 because the end of the loop is
      // inclusive and would otherwise create 81 buckets
      unit = unit == 0
        ? 1
        : roundToNearestPowerOfTen(unit);
    }
    if (!VariableType.DOUBLE.equals(getVariableType(context))) {
      // round unit up if grouped by number variable without decimal point
      unit = Math.ceil(unit);
    }
    return unit;
  }

  private String getKeyForNumberBucket(final VariableType varType,
                                       final double bucketStart) {
    if (!VariableType.DOUBLE.equals(varType)) {
      // truncate decimal point for non-double variable aggregations
      return String.valueOf((long) bucketStart);
    }
    DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
    final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);
    return decimalFormat.format(bucketStart);
  }

  private GroupByDateUnit getGroupByDateUnit(final ExecutionContext<Data> context) {
    return context.getReportData().getConfiguration().getGroupByDateVariableUnit();
  }

  private Double roundBaselineToNearestPowerOfTen(Double baselineToRound) {
    if (baselineToRound >= 0) {
      return roundDownToNearestPowerOfTen(baselineToRound);
    } else {
      // round up if baseline is negative and apply sign again after rounding
      return roundUpToNearestPowerOfTen(Math.abs(baselineToRound)) * -1;
    }
  }

  private Double roundDownToNearestPowerOfTen(Double numberToRound) {
    return Math.pow(10, Math.floor(Math.log10(numberToRound)));
  }

  private Double roundUpToNearestPowerOfTen(Double numberToRound) {
    return Math.pow(10, Math.ceil(Math.log10(numberToRound)));
  }

  private Double roundToNearestPowerOfTen(Double numberToRound) {
    return Math.pow(10, Math.round(Math.log10(numberToRound)));
  }

  protected boolean isGroupedByNumberVariable(final VariableType varType) {
    return VariableType.getNumericTypes().contains(varType);
  }
}
