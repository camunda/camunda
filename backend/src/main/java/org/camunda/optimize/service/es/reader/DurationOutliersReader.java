/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.percentiles.PercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.PercentileRanksAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.aggregations.metrics.stats.StatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStats;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class DurationOutliersReader {
  private static final String HISTOGRAM_AGG = "histogram";
  private static final String STATS_AGG = "stats";
  private static final String FILTERED_FLOW_NODES_AGG = "filteredFlowNodes";
  private static final String NESTED_AGG = "nested";
  private static final String RANKS_AGG = "ranks_agg";

  private TenantAuthorizationService tenantAuthorizationService;
  private final OptimizeElasticsearchClient esClient;
  private ProcessDefinitionReader processDefinitionReader;

  public List<DurationChartEntryDto> getCountByDurationChart(final String procDefKey,
                                                             final List<String> procDefVersion,
                                                             final String flowNodeId,
                                                             final String userId,
                                                             final List<String> tenantId,
                                                             final Long lowerOutlierBound,
                                                             final Long higherOutlierBound) {
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, tenantId)) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided tenant");
    }

    final BoolQueryBuilder query = DefinitionQueryUtil.createDefinitionQuery(
      procDefKey,
      procDefVersion,
      tenantId,
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );

    long interval = getInterval(query, flowNodeId);
    HistogramAggregationBuilder histogram = AggregationBuilders.histogram(HISTOGRAM_AGG)
      .field(EVENTS + "." + ProcessInstanceIndex.DURATION)
      .interval(interval);

    NestedAggregationBuilder termsAgg = buildNestedFlowNodeFilterAggregation(flowNodeId, histogram);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(termsAgg)
      .size(0);

    SearchRequest searchRequest =
      new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
        .types(PROCESS_INSTANCE_INDEX_NAME)
        .source(searchSourceBuilder);

    SearchResponse search;
    try {
      search = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.warn("Couldn't retrieve duration chart");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }

    return ((Histogram) ((Filter) ((Nested) search.getAggregations().get(EVENTS)).getAggregations()
      .get(FILTERED_FLOW_NODES_AGG)).getAggregations().get(HISTOGRAM_AGG)).getBuckets()
      .stream()
      .map(b -> {
        final Long durationKey = Double.valueOf(b.getKeyAsString()).longValue();
        return new DurationChartEntryDto(
          durationKey,
          b.getDocCount(),
          isOutlier(lowerOutlierBound, higherOutlierBound, durationKey)
        );
      })
      .collect(Collectors.toList());
  }

  public Map<String, FindingsDto> getFlowNodeOutlierMap(final String procDefKey,
                                                        final List<String> procDefVersion,
                                                        final String userId,
                                                        final List<String> tenantId) {
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, tenantId)) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided tenant");
    }

    final BoolQueryBuilder processInstanceQuery = DefinitionQueryUtil.createDefinitionQuery(
      procDefKey,
      procDefVersion,
      tenantId,
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
    ExtendedStatsAggregationBuilder stats = AggregationBuilders.extendedStats(STATS_AGG)
      .field(EVENTS + "." + ACTIVITY_DURATION);

    TermsAggregationBuilder terms = AggregationBuilders.terms(EVENTS)
      .size(LIST_FETCH_LIMIT)
      .field(EVENTS + "." + ProcessInstanceIndex.ACTIVITY_ID)
      .subAggregation(stats);

    NestedAggregationBuilder nested = AggregationBuilders.nested(NESTED_AGG, EVENTS)
      .subAggregation(terms);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .aggregation(nested)
      .size(0);

    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    Aggregations flowNodeDurationStatAggregations;
    try {
      flowNodeDurationStatAggregations = esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations();
    } catch (IOException e) {
      log.warn("Couldn't retrieve outliers from Elasticsearch");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }

    return createFlowNodeOutlierMap(processInstanceQuery, flowNodeDurationStatAggregations);
  }

  private Map<String, FindingsDto> createFlowNodeOutlierMap(final BoolQueryBuilder processInstanceQuery,
                                                            final Aggregations flowNodeDurationStatAggregations) {
    final List<? extends Terms.Bucket> buckets =
      ((Terms) ((Nested) flowNodeDurationStatAggregations.get(NESTED_AGG)).getAggregations()
        .get(EVENTS))
        .getBuckets();

    final Map<String, ExtendedStats> statsByFlowNodeId = new HashMap<>();
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .size(0);
    final NestedAggregationBuilder nestedFlowNodeAggregation = AggregationBuilders.nested(EVENTS, EVENTS);
    searchSourceBuilder.aggregation(nestedFlowNodeAggregation);
    buckets
      .forEach((bucket) -> {
        final String flowNodeId = bucket.getKeyAsString();
        final ExtendedStats statsAgg = bucket.getAggregations().get(STATS_AGG);
        statsByFlowNodeId.put(flowNodeId, statsAgg);

        if (statsAgg.getStdDeviation() != 0.0D) {
          double stdDeviationBoundLower = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.LOWER);
          double stdDeviationBoundHigher = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.UPPER);

          PercentileRanksAggregationBuilder percentileRanks = AggregationBuilders.percentileRanks(
            RANKS_AGG, new double[]{stdDeviationBoundLower, stdDeviationBoundHigher}
          ).field(EVENTS + "." + ACTIVITY_DURATION);

          final TermQueryBuilder terms = QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, flowNodeId);
          final FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(
            getFilteredFlowNodeAggregationName(flowNodeId), terms
          );
          filteredFlowNodes.subAggregation(percentileRanks);
          nestedFlowNodeAggregation.subAggregation(filteredFlowNodes);
        }
      });

    final SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);
    try {
      final Aggregations allFlowNodesPercentileRanks = esClient.search(searchRequest, RequestOptions.DEFAULT)
        .getAggregations();
      final Aggregations allFlowNodeFilterAggs = ((Nested) allFlowNodesPercentileRanks.get(EVENTS)).getAggregations();
      return mapToFlowNodeFindingsMap(statsByFlowNodeId, allFlowNodeFilterAggs);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }
  }

  private Map<String, FindingsDto> mapToFlowNodeFindingsMap(final Map<String, ExtendedStats> statsByFlowNodeId,
                                                            final Aggregations allFlowNodeFilterAggs) {
    final AtomicLong totalLowerOutlierCount = new AtomicLong(0L);
    final AtomicLong totalHigherOutlierCount = new AtomicLong(0L);
    final Map<String, FindingsDto> findingsDtoMap = statsByFlowNodeId.entrySet().stream()
      .map(flowNodeStatsEntry -> {
        final String flowNodeId = flowNodeStatsEntry.getKey();
        final ExtendedStats stats = flowNodeStatsEntry.getValue();
        final FindingsDto finding = new FindingsDto();

        if (stats.getStdDeviation() != 0.0D
          && allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId)) != null) {
          final PercentileRanks ranks =
            (PercentileRanks) ((Filter) allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId)))
              .getAggregations().iterator().next();

          double avg = stats.getAvg();
          double stdDeviationBoundLower = stats.getStdDeviationBound(ExtendedStats.Bounds.LOWER);
          double stdDeviationBoundHigher = stats.getStdDeviationBound(ExtendedStats.Bounds.UPPER);

          if (stdDeviationBoundLower > stats.getMin()) {
            double percent = ranks.percent(stdDeviationBoundLower);
            final long count = (long) Math.ceil(stats.getCount() * 0.01 * percent);
            finding.setLowerOutlier(
              (long) stdDeviationBoundLower, percent, avg / stdDeviationBoundLower, count
            );
            totalLowerOutlierCount.addAndGet(count);
          }

          if (stdDeviationBoundHigher < stats.getMax()) {
            double percent = ranks.percent(stdDeviationBoundHigher);
            final long count = (long) Math.ceil(stats.getCount() * 0.01 * (100 - percent));
            finding.setHigherOutlier(
              (long) stdDeviationBoundHigher, 100 - percent, stdDeviationBoundHigher / avg, count
            );
            totalHigherOutlierCount.addAndGet(count);
          }
        }

        return new AbstractMap.SimpleEntry<>(flowNodeId, finding);
      })
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    final long totalOutlierCount = totalLowerOutlierCount.get() + totalHigherOutlierCount.get();
    findingsDtoMap.values().forEach(finding -> {
      finding.getLowerOutlier().ifPresent(
        lowerOutlier -> finding.setLowerOutlierHeat((double) lowerOutlier.getCount() / totalLowerOutlierCount.get())
      );
      finding.getHigherOutlier().ifPresent(
        higherOutlier -> finding.setHigherOutlierHeat((double) higherOutlier.getCount() / totalHigherOutlierCount.get())
      );
      finding.setHeat((double) finding.getOutlierCount() / totalOutlierCount);
    });
    return findingsDtoMap;
  }

  private String getFilteredFlowNodeAggregationName(final String flowNodeId) {
    return FILTERED_FLOW_NODES_AGG + flowNodeId;
  }

  private boolean isOutlier(final Long lowerOutlierBound, final Long higherOutlierBound, final Long durationValue) {
    return Optional.ofNullable(lowerOutlierBound).map(value -> durationValue <= value).orElse(false)
      || Optional.ofNullable(higherOutlierBound).map(value -> durationValue >= value).orElse(false);
  }

  private long getInterval(final BoolQueryBuilder query, final String flowNodeId) {
    StatsAggregationBuilder statsAgg = AggregationBuilders.stats(STATS_AGG)
      .field(EVENTS + "." + ProcessInstanceIndex.DURATION);

    NestedAggregationBuilder termsAgg = buildNestedFlowNodeFilterAggregation(flowNodeId, statsAgg);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(termsAgg)
      .size(0);

    SearchRequest searchRequest =
      new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
        .types(PROCESS_INSTANCE_INDEX_NAME)
        .source(searchSourceBuilder);

    SearchResponse search;
    try {
      search = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }

    double min = ((Stats) ((Filter) ((Nested) search.getAggregations()
      .get(EVENTS)).getAggregations().get(FILTERED_FLOW_NODES_AGG)).getAggregations()
      .get(STATS_AGG)).getMin();
    double max = ((Stats) ((Filter) ((Nested) search.getAggregations()
      .get(EVENTS)).getAggregations().get(FILTERED_FLOW_NODES_AGG)).getAggregations()
      .get(STATS_AGG)).getMax();

    return (long) Math.ceil((max - min) / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
  }

  private NestedAggregationBuilder buildNestedFlowNodeFilterAggregation(final String flowNodeId,
                                                                        final AggregationBuilder subAggregation) {
    TermQueryBuilder terms = QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, flowNodeId);

    FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(FILTERED_FLOW_NODES_AGG, terms);
    filteredFlowNodes.subAggregation(subAggregation);

    return AggregationBuilders.nested(EVENTS, EVENTS).subAggregation(filteredFlowNodes);
  }
}
