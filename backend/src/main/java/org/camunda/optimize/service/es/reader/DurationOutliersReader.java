/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.camunda.optimize.dto.optimize.query.FlowNodeOutlierParametersDto;
import org.camunda.optimize.dto.optimize.query.ProcessDefinitionParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.HasAggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.aggregations.metrics.stats.StatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStats;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class DurationOutliersReader {
  private static final String AGG_HISTOGRAM = "histogram";
  private static final String AGG_STATS = "stats";
  private static final String AGG_FILTERED_FLOW_NODES = "filteredFlowNodes";
  private static final String AGG_NESTED = "nested";
  private static final String AGG_REVERSE_NESTED_PROCESS_INSTANCE = "processInstance";
  private static final String AGG_VARIABLES = "variables";
  private static final String AGG_VARIABLE_VALUE_TERMS = "variableValueTerms";
  private static final String LOWER_DURATION_AGG = "lowerDurationAgg";
  private static final String HIGHER_DURATION_AGG = "higherDurationAgg";

  private TenantAuthorizationService tenantAuthorizationService;
  private final OptimizeElasticsearchClient esClient;
  private ProcessDefinitionReader processDefinitionReader;
  private ProcessVariableService processVariableService;

  public List<DurationChartEntryDto> getCountByDurationChart(final FlowNodeOutlierParametersDto outlierParams,
                                                             final String userId) {
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, outlierParams.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided tenant");
    }

    final BoolQueryBuilder query = createProcessDefinitionFilterQuery(outlierParams);

    long interval = getInterval(query, outlierParams.getFlowNodeId());
    HistogramAggregationBuilder histogram = AggregationBuilders.histogram(AGG_HISTOGRAM)
      .field(EVENTS + "." + ProcessInstanceIndex.DURATION)
      .interval(interval);

    NestedAggregationBuilder termsAgg = buildNestedFlowNodeFilterAggregation(outlierParams.getFlowNodeId(), histogram);

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
      .get(AGG_FILTERED_FLOW_NODES)).getAggregations().get(AGG_HISTOGRAM)).getBuckets()
      .stream()
      .map(b -> {
        final Long durationKey = Double.valueOf(b.getKeyAsString()).longValue();
        return new DurationChartEntryDto(
          durationKey,
          b.getDocCount(),
          isOutlier(outlierParams.getLowerOutlierBound(), outlierParams.getHigherOutlierBound(), durationKey)
        );
      })
      .collect(Collectors.toList());
  }

  public Map<String, FindingsDto> getFlowNodeOutlierMap(final ProcessDefinitionParametersDto processDefinitionParams,
                                                        final String userId) {
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, processDefinitionParams.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided tenant");
    }

    final BoolQueryBuilder processInstanceQuery = createProcessDefinitionFilterQuery(processDefinitionParams);
    ExtendedStatsAggregationBuilder stats = AggregationBuilders.extendedStats(AGG_STATS)
      .field(EVENTS + "." + ACTIVITY_DURATION);

    TermsAggregationBuilder terms = AggregationBuilders.terms(EVENTS)
      .size(LIST_FETCH_LIMIT)
      .field(EVENTS + "." + ProcessInstanceIndex.ACTIVITY_ID)
      .subAggregation(stats);

    NestedAggregationBuilder nested = AggregationBuilders.nested(AGG_NESTED, EVENTS)
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

  public List<VariableTermDto> getSignificantOutlierVariableTerms(final FlowNodeOutlierParametersDto outlierParams,
                                                                  final String userId) {
    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, outlierParams.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided tenant");
    }

    if (outlierParams.getLowerOutlierBound() == null && outlierParams.getHigherOutlierBound() == null) {
      throw new OptimizeValidationException("One of lowerOutlierBound or higherOutlierBound must be set.");
    }

    try {
      // #1 get top variable value terms of outliers
      final ParsedReverseNested outlierNestedProcessInstancesAgg = getTopVariableTermsOfOutliers(
        outlierParams, userId
      );
      final Map<String, Map<String, Long>> outlierVariableTermOccurrences = createVariableTermOccurrencesMap(
        outlierNestedProcessInstancesAgg.getAggregations().get(AGG_VARIABLES)
      );
      final long outlierProcessInstanceCount = outlierNestedProcessInstancesAgg.getDocCount();
      final Map<String, Set<String>> outlierVariableTerms = outlierVariableTermOccurrences.entrySet().stream()
        .map(variableAndTerms -> new AbstractMap.SimpleEntry<>(
          variableAndTerms.getKey(), variableAndTerms.getValue().keySet()
        ))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      if (outlierProcessInstanceCount == 0) {
        throw new NotFoundException(
          String.format("No outliers found for the provided parameters [%s]", outlierParams.toString())
        );
      }

      // #2 get counts of the same terms from non outlier instances
      final ParsedReverseNested nonOutlierNestedProcessInstancesAgg = getVariableTermOccurrencesOfNonOutliers(
        outlierParams, outlierVariableTerms
      );
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence = createVariableTermOccurrencesMap(
        nonOutlierNestedProcessInstancesAgg.getAggregations().get(AGG_VARIABLES)
      );
      final long nonOutlierProcessInstanceCount = nonOutlierNestedProcessInstancesAgg.getDocCount();

      // #3 compare both data sets and only keep terms whose frequency is considered significant
      final long totalProcessInstanceCount = outlierProcessInstanceCount + nonOutlierProcessInstanceCount;
      final Map<String, Map<String, Long>> outlierSignificantVariableTerms = filterSignificantOutlierVariableTerms(
        outlierVariableTermOccurrences,
        nonOutlierVariableTermOccurrence,
        outlierProcessInstanceCount,
        nonOutlierProcessInstanceCount
      );

      return mapToVariableTermList(
        outlierSignificantVariableTerms,
        nonOutlierVariableTermOccurrence,
        outlierProcessInstanceCount,
        nonOutlierProcessInstanceCount,
        totalProcessInstanceCount
      );

    } catch (IOException e) {
      log.warn("Couldn't determine significant outlier variable terms.");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }
  }

  private ParsedReverseNested getVariableTermOccurrencesOfNonOutliers(final FlowNodeOutlierParametersDto outlierParams,
                                                                      final Map<String, Set<String>> outlierVariableTerms)
    throws IOException {
    final SearchRequest nonOutliersTermOccurrencesRequest = createTopVariableTermsOfNonOutliersQuery(
      outlierParams, outlierVariableTerms
    );
    return extractNestedProcessInstanceAgg(
      esClient.search(nonOutliersTermOccurrencesRequest, RequestOptions.DEFAULT)
    );
  }

  private ParsedReverseNested getTopVariableTermsOfOutliers(final FlowNodeOutlierParametersDto outlierParams,
                                                            final String userId) throws IOException {
    final List<String> variableNames = processVariableService.getVariableNames(
      userId,
      new ProcessVariableNameRequestDto(
        outlierParams.getProcessDefinitionKey(),
        outlierParams.getProcessDefinitionVersions(),
        outlierParams.getTenantIds(),
        ""
      )
    ).stream().map(ProcessVariableNameResponseDto::getName).collect(Collectors.toList());

    final SearchRequest outlierTopVariableTermsRequest = createTopVariableTermsOfOutliersQuery(
      outlierParams, variableNames
    );
    return extractNestedProcessInstanceAgg(
      esClient.search(outlierTopVariableTermsRequest, RequestOptions.DEFAULT)
    );
  }

  private List<VariableTermDto> mapToVariableTermList(
    final Map<String, Map<String, Long>> outlierSignificantVariableTerms,
    final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
    final long outlierProcessInstanceCount,
    final long nonOutlierProcessInstanceCount,
    final long totalProcessInstanceCount) {

    return outlierSignificantVariableTerms.entrySet().stream()
      .flatMap(significantVariableTerms -> significantVariableTerms.getValue().entrySet().stream()
        .map(termAndCount -> {
               final String variableName = significantVariableTerms.getKey();
               final Long outlierTermOccurrence = termAndCount.getValue();
               return new VariableTermDto(
                 variableName,
                 termAndCount.getKey(),
                 outlierTermOccurrence,
                 getRatio(outlierProcessInstanceCount, outlierTermOccurrence),
                 Optional.ofNullable(nonOutlierVariableTermOccurrence.get(variableName))
                   .flatMap(entry -> Optional.ofNullable(entry.get(termAndCount.getKey())))
                   .map(nonOutlierTermOccurrence -> getRatio(nonOutlierProcessInstanceCount, nonOutlierTermOccurrence))
                   .orElse(0.0D),
                 getRatio(totalProcessInstanceCount, outlierTermOccurrence)
               );
             }
        )
      )
      .sorted(Comparator.comparing(VariableTermDto::getInstanceCount).reversed())
      .collect(Collectors.toList());
  }

  private SearchRequest createTopVariableTermsOfOutliersQuery(final FlowNodeOutlierParametersDto outlierParams,
                                                              final List<String> variableNames) {
    final BoolQueryBuilder flowNodeFilterQuery = QueryBuilders.boolQuery()
      .must(QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, outlierParams.getFlowNodeId()))
      .minimumShouldMatch(1);

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
        QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).gte(outlierParams.getHigherOutlierBound())
      );
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
        QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).lte(outlierParams.getLowerOutlierBound())
      );
    }

    final NestedAggregationBuilder nestedVariableAggregation = AggregationBuilders.nested(AGG_VARIABLES, VARIABLES);
    variableNames.stream().distinct().forEach(variableName -> {
      nestedVariableAggregation.subAggregation(
        AggregationBuilders.filter(
          variableName, QueryBuilders.termQuery(VARIABLES + "." + VARIABLE_NAME, variableName)
        ).subAggregation(
          AggregationBuilders.terms(AGG_VARIABLE_VALUE_TERMS).field(VARIABLES + "." + VARIABLE_VALUE)
            // This corresponds to the min doc count also used by elasticsearch's own significant terms implementation
            // and serves the purpose to ignore high cardinality values
            // @formatter:off
            // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-significantterms-aggregation.html
            // @formatter:on
            .minDocCount(3)
        )
      );
    });

    return createFilteredFlowNodeVariableAggregation(outlierParams, flowNodeFilterQuery, nestedVariableAggregation);
  }

  private SearchRequest createTopVariableTermsOfNonOutliersQuery(final FlowNodeOutlierParametersDto outlierParams,
                                                                 final Map<String, Set<String>> variablesAndTerms) {
    final BoolQueryBuilder flowNodeFilterQuery = QueryBuilders.boolQuery()
      .must(QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, outlierParams.getFlowNodeId()))
      .minimumShouldMatch(1);

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
        QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).lt(outlierParams.getHigherOutlierBound())
      );
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
        QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).gt(outlierParams.getLowerOutlierBound())
      );
    }

    final NestedAggregationBuilder nestedVariableAggregation = AggregationBuilders.nested(AGG_VARIABLES, VARIABLES);
    variablesAndTerms
      .forEach((variableName, value) -> nestedVariableAggregation.subAggregation(
        AggregationBuilders.filter(
          variableName, QueryBuilders.termQuery(VARIABLES + "." + VARIABLE_NAME, variableName)
        ).subAggregation(
          AggregationBuilders.terms(AGG_VARIABLE_VALUE_TERMS).field(VARIABLES + "." + VARIABLE_VALUE)
            // only include provided terms
            .includeExclude(new IncludeExclude(value.toArray(new String[]{}), null))
        )
      ));

    return createFilteredFlowNodeVariableAggregation(outlierParams, flowNodeFilterQuery, nestedVariableAggregation);
  }

  private SearchRequest createFilteredFlowNodeVariableAggregation(final FlowNodeOutlierParametersDto outlierParams,
                                                                  final BoolQueryBuilder flowNodeFilterQuery,
                                                                  final NestedAggregationBuilder nestedVariableAggregation) {
    final FilterAggregationBuilder flowNodeFilterAggregation = AggregationBuilders.filter(
      AGG_FILTERED_FLOW_NODES, flowNodeFilterQuery
    );
    flowNodeFilterAggregation.subAggregation(
      AggregationBuilders.reverseNested(AGG_REVERSE_NESTED_PROCESS_INSTANCE)
        .subAggregation(nestedVariableAggregation)
    );
    final NestedAggregationBuilder nestedFlowNodeAggregation = AggregationBuilders.nested(EVENTS, EVENTS)
      .subAggregation(flowNodeFilterAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(createProcessDefinitionFilterQuery(outlierParams))
      .fetchSource(false)
      .aggregation(nestedFlowNodeAggregation)
      .size(0);

    return new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);
  }

  private Map<String, Map<String, Long>> filterSignificantOutlierVariableTerms(
    final Map<String, Map<String, Long>> outlierVariableTermOccurrences,
    final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
    final long outlierProcessInstanceCount,
    final long nonOutlierProcessInstanceCount) {

    return outlierVariableTermOccurrences.entrySet()
      .stream()
      .map(outlierVariableTermOccurrence -> {
        final String variableName = outlierVariableTermOccurrence.getKey();
        final Map<String, Long> outlierTermOccurrences = outlierVariableTermOccurrence.getValue();
        final Map<String, Long> nonOutlierTermOccurrences = nonOutlierVariableTermOccurrence
          .getOrDefault(variableName, Collections.emptyMap());

        final Map<String, Long> significantTerms = outlierTermOccurrences.entrySet().stream()
          .filter(outlierTermAndCount -> {
            final String term = outlierTermAndCount.getKey();
            final Long outlierTermCount = outlierTermAndCount.getValue();
            final Long nonOutlierTermCount = nonOutlierTermOccurrences.getOrDefault(term, 0L);

            final boolean isMoreFrequentInOutlierSet =
              getRatio(outlierProcessInstanceCount, outlierTermCount)
                > getRatio(nonOutlierProcessInstanceCount, nonOutlierTermCount);

            final boolean isSignificant = TestUtils.chiSquareTestDataSetsComparison(
              new long[]{nonOutlierTermCount, nonOutlierProcessInstanceCount},
              new long[]{outlierTermCount, outlierProcessInstanceCount},
              // This is the confidence level or alpha that defines the degree of confidence of the test result.
              // The test returns true if the null hypothesis (both datasets orignate from the same distribution)
              // can be rejected with 100 * (1 - alpha) percent confidence and thus the sets can be considered
              // to be significantly different
              0.001D
            );

            return isMoreFrequentInOutlierSet && isSignificant;
          })
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new AbstractMap.SimpleEntry<>(variableName, significantTerms);
      })
      .filter(stringMapSimpleEntry -> stringMapSimpleEntry.getValue().size() > 0)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, Map<String, Long>> createVariableTermOccurrencesMap(final HasAggregations allVariableAggregations) {
    final Map<String, Map<String, Long>> outlierVariableTermOccurrences = new HashMap<>();
    allVariableAggregations.getAggregations().forEach(aggregation -> {
      final Filter variableFilterAggregation = (Filter) aggregation;
      final Terms variableValueTerms = variableFilterAggregation.getAggregations().get(AGG_VARIABLE_VALUE_TERMS);

      if (variableValueTerms.getBuckets().size() > 0) {
        final Map<String, Long> termOccurrences = variableValueTerms.getBuckets().stream()
          .map(bucket -> (Terms.Bucket) bucket)
          .map(bucket -> new AbstractMap.SimpleEntry<>(bucket.getKeyAsString(), bucket.getDocCount()))
          .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        final String variableName = variableFilterAggregation.getName();
        outlierVariableTermOccurrences.put(variableName, termOccurrences);
      }
    });
    return outlierVariableTermOccurrences;
  }

  private Map<String, FindingsDto> createFlowNodeOutlierMap(final BoolQueryBuilder processInstanceQuery,
                                                            final Aggregations flowNodeDurationStatAggregations) {
    final List<? extends Terms.Bucket> buckets =
      ((Terms) ((Nested) flowNodeDurationStatAggregations.get(AGG_NESTED)).getAggregations()
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
        final ExtendedStats statsAgg = bucket.getAggregations().get(AGG_STATS);
        statsByFlowNodeId.put(flowNodeId, statsAgg);

        if (statsAgg.getStdDeviation() != 0.0D) {
          double stdDeviationBoundLower = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.LOWER);
          double stdDeviationBoundHigher = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.UPPER);

          final FilterAggregationBuilder lowerOutlierEventFilter = AggregationBuilders.filter(
            LOWER_DURATION_AGG,
            QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).lte(stdDeviationBoundLower)
          );

          final FilterAggregationBuilder higherOutlierEventFilter = AggregationBuilders.filter(
            HIGHER_DURATION_AGG,
            QueryBuilders.rangeQuery(EVENTS + "." + ACTIVITY_DURATION).gte(stdDeviationBoundHigher)
          );

          final TermQueryBuilder terms = QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, flowNodeId);
          final FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(
            getFilteredFlowNodeAggregationName(flowNodeId), terms
          );
          filteredFlowNodes.subAggregation(lowerOutlierEventFilter);
          filteredFlowNodes.subAggregation(higherOutlierEventFilter);
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
          final Filter flowNodeFilterAgg = (Filter) allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(
            flowNodeId));
          final Filter lowerOutlierFilterAgg = (Filter) flowNodeFilterAgg.getAggregations().get(LOWER_DURATION_AGG);
          final Filter higherOutlierFilterAgg = (Filter) flowNodeFilterAgg.getAggregations().get(HIGHER_DURATION_AGG);

          double avg = stats.getAvg();
          double stdDeviationBoundLower = stats.getStdDeviationBound(ExtendedStats.Bounds.LOWER);
          double stdDeviationBoundHigher = stats.getStdDeviationBound(ExtendedStats.Bounds.UPPER);

          if (stdDeviationBoundLower > stats.getMin() && lowerOutlierFilterAgg.getDocCount() > 0L) {
            final long count = lowerOutlierFilterAgg.getDocCount();
            double percent = (double) count / flowNodeFilterAgg.getDocCount();
            finding.setLowerOutlier(
              (long) stdDeviationBoundLower, percent, avg / stdDeviationBoundLower, count
            );
            totalLowerOutlierCount.addAndGet(count);
          }

          if (stdDeviationBoundHigher < stats.getMax() && higherOutlierFilterAgg.getDocCount() > 0) {
            final long count = higherOutlierFilterAgg.getDocCount();
            double percent = (double) count / flowNodeFilterAgg.getDocCount();
            finding.setHigherOutlier(
              (long) stdDeviationBoundHigher, percent, stdDeviationBoundHigher / avg, count
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
        lowerOutlier -> finding.setLowerOutlierHeat(getRatio(totalLowerOutlierCount.get(), lowerOutlier.getCount()))
      );
      finding.getHigherOutlier().ifPresent(
        higherOutlier -> finding.setHigherOutlierHeat(getRatio(totalHigherOutlierCount.get(), higherOutlier.getCount()))
      );
      finding.setHeat(getRatio(totalOutlierCount, finding.getOutlierCount()));
    });
    return findingsDtoMap;
  }

  private String getFilteredFlowNodeAggregationName(final String flowNodeId) {
    return AGG_FILTERED_FLOW_NODES + flowNodeId;
  }

  private boolean isOutlier(final Long lowerOutlierBound, final Long higherOutlierBound, final Long durationValue) {
    return Optional.ofNullable(lowerOutlierBound).map(value -> durationValue <= value).orElse(false)
      || Optional.ofNullable(higherOutlierBound).map(value -> durationValue >= value).orElse(false);
  }

  private long getInterval(final BoolQueryBuilder query, final String flowNodeId) {
    StatsAggregationBuilder statsAgg = AggregationBuilders.stats(AGG_STATS)
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
      .get(EVENTS)).getAggregations().get(AGG_FILTERED_FLOW_NODES)).getAggregations()
      .get(AGG_STATS)).getMin();
    double max = ((Stats) ((Filter) ((Nested) search.getAggregations()
      .get(EVENTS)).getAggregations().get(AGG_FILTERED_FLOW_NODES)).getAggregations()
      .get(AGG_STATS)).getMax();

    return (long) Math.ceil((max - min) / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
  }

  private NestedAggregationBuilder buildNestedFlowNodeFilterAggregation(final String flowNodeId,
                                                                        final AggregationBuilder subAggregation) {
    TermQueryBuilder terms = QueryBuilders.termQuery(EVENTS + "." + ACTIVITY_ID, flowNodeId);

    FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(AGG_FILTERED_FLOW_NODES, terms);
    filteredFlowNodes.subAggregation(subAggregation);

    return AggregationBuilders.nested(EVENTS, EVENTS).subAggregation(filteredFlowNodes);
  }

  private ParsedReverseNested extractNestedProcessInstanceAgg(final SearchResponse outlierTopVariableTermsResponse) {
    return ((HasAggregations) ((HasAggregations) outlierTopVariableTermsResponse.getAggregations()
      .get(EVENTS)).getAggregations().get(AGG_FILTERED_FLOW_NODES))
      .getAggregations()
      .get(AGG_REVERSE_NESTED_PROCESS_INSTANCE);
  }

  private BoolQueryBuilder createProcessDefinitionFilterQuery(
    final ProcessDefinitionParametersDto processDefinitionParams) {
    return DefinitionQueryUtil.createDefinitionQuery(
      processDefinitionParams.getProcessDefinitionKey(),
      processDefinitionParams.getProcessDefinitionVersions(),
      processDefinitionParams.getTenantIds(),
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
  }

  private double getRatio(final long totalCount, final long observedCount) {
    return (double) observedCount / totalCount;
  }
}
