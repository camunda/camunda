/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.DefinitionQueryUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
import org.elasticsearch.search.aggregations.metrics.ExtendedStats;
import org.elasticsearch.search.aggregations.metrics.ExtendedStatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.aggregations.metrics.StatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_CANCEL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.BOUNDARY_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.CALL_ACTIVITY;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_CANCEL;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_NONE;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.END_EVENT_TERMINATE;
import static org.camunda.bpm.engine.ActivityTypes.GATEWAY_COMPLEX;
import static org.camunda.bpm.engine.ActivityTypes.GATEWAY_EVENT_BASED;
import static org.camunda.bpm.engine.ActivityTypes.GATEWAY_EXCLUSIVE;
import static org.camunda.bpm.engine.ActivityTypes.GATEWAY_INCLUSIVE;
import static org.camunda.bpm.engine.ActivityTypes.GATEWAY_PARALLEL;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_CATCH;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_COMPENSATION_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_ESCALATION_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_LINK;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_MESSAGE_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_NONE_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_SIGNAL_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_THROW;
import static org.camunda.bpm.engine.ActivityTypes.INTERMEDIATE_EVENT_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_COMPENSATION;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_CONDITIONAL;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_ERROR;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_ESCALATION;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_MESSAGE;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_SIGNAL;
import static org.camunda.bpm.engine.ActivityTypes.START_EVENT_TIMER;
import static org.camunda.bpm.engine.ActivityTypes.TASK_MANUAL_TASK;
import static org.camunda.bpm.engine.ActivityTypes.TASK_USER_TASK;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

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
  private static final String FLOW_NODE_ID_AGG = "flowNodeId";
  private static final String FLOW_NODE_TYPE_FILTER = "flowNodeTypeFilter";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessVariableReader processVariableReader;
  private final ConfigurationService configurationService;

  public List<DurationChartEntryDto> getCountByDurationChart(final FlowNodeOutlierParametersDto outlierParams) {
    final BoolQueryBuilder query = createProcessDefinitionFilterQuery(outlierParams);

    long interval = getInterval(query, outlierParams.getFlowNodeId(), outlierParams.getProcessDefinitionKey());
    HistogramAggregationBuilder histogram = AggregationBuilders.histogram(AGG_HISTOGRAM)
      .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
      .interval(interval);

    NestedAggregationBuilder termsAgg = buildNestedFlowNodeFilterAggregation(outlierParams.getFlowNodeId(), histogram);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(termsAgg)
      .size(0);

    SearchRequest searchRequest =
      new SearchRequest(getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()))
        .source(searchSourceBuilder);

    SearchResponse search;
    try {
      search = esClient.search(searchRequest);
    } catch (IOException e) {
      log.warn("Couldn't retrieve duration chart");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to evaluate count by duration chart because instance index with alias {} does not exist. " +
            "Returning empty list.",
          getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey())
        );
        return Collections.emptyList();
      }
      throw e;
    }

    return ((Histogram) ((Filter) ((Nested) search.getAggregations().get(FLOW_NODE_INSTANCES)).getAggregations()
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

  public Map<String, FindingsDto> getFlowNodeOutlierMap(final ProcessDefinitionParametersDto processDefinitionParams) {
    final BoolQueryBuilder processInstanceQuery = createProcessDefinitionFilterQuery(processDefinitionParams);
    ExtendedStatsAggregationBuilder stats = AggregationBuilders.extendedStats(AGG_STATS)
      .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION);

    final BoolQueryBuilder query = boolQuery();
    if(processDefinitionParams.isDisconsiderAutomatedTasks())
    {
      query.filter(termsQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE, generateListOfHumanTasks()));
    }
    else
    {
      query.filter(boolQuery().mustNot(termsQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE,
                                                  generateListOfStandardExcludedFlowNodeTypes())));
    }

    AggregationBuilder aggregationflowNodeTypeAndId = AggregationBuilders
      .filter(FLOW_NODE_TYPE_FILTER, query)
      .subAggregation(
        AggregationBuilders
          .terms(FLOW_NODE_ID_AGG).field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
          .size(configurationService.getEsAggregationBucketLimit())
          .subAggregation(stats)
      );

    NestedAggregationBuilder nested = AggregationBuilders.nested(AGG_NESTED, FLOW_NODE_INSTANCES)
      .subAggregation(aggregationflowNodeTypeAndId);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .aggregation(nested)
      .size(0);

    SearchRequest searchRequest =
      new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionParams.getProcessDefinitionKey()))
        .source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = "Could not fetch data to generate Outlier Analysis Heatmap";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final List<? extends Terms.Bucket> deviationForEachFlowNode = searchResponse.getAggregations()
      .<Nested>get(AGG_NESTED)
      .getAggregations()
      .<Filter>get(
        FLOW_NODE_TYPE_FILTER)
      .getAggregations()
      .<Terms>get(FLOW_NODE_ID_AGG)
      .getBuckets();

    return createFlowNodeOutlierMap(
      deviationForEachFlowNode,
      processInstanceQuery,
      processDefinitionParams
    );
  }

  public List<VariableTermDto> getSignificantOutlierVariableTerms(final FlowNodeOutlierParametersDto outlierParams) {
    if (outlierParams.getLowerOutlierBound() == null && outlierParams.getHigherOutlierBound() == null) {
      throw new OptimizeValidationException("One of lowerOutlierBound or higherOutlierBound must be set.");
    }

    try {
      // #1 get top variable value terms of outliers
      final ParsedReverseNested outlierNestedProcessInstancesAgg = getTopVariableTermsOfOutliers(outlierParams);
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
        return new ArrayList<>();
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
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to determine significant outlier variable terms because instance index with name {} does not " +
            "exist. Returning empty list.",
          getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey())
        );
        return Collections.emptyList();
      }
      throw e;
    }
  }

  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(final FlowNodeOutlierVariableParametersDto outlierParams) {
    // filter by definition
    final BoolQueryBuilder mainFilterQuery = createProcessDefinitionFilterQuery(outlierParams);
    // flowNode id & outlier duration
    final BoolQueryBuilder flowNodeFilterQuery = createFlowNodeOutlierQuery(outlierParams);
    mainFilterQuery.must(nestedQuery(FLOW_NODE_INSTANCES, flowNodeFilterQuery, ScoreMode.None));
    // variable name & term
    final BoolQueryBuilder variableTermFilterQuery = boolQuery()
      .must(termQuery(VARIABLES + "." + VARIABLE_NAME, outlierParams.getVariableName()))
      .must(termQuery(VARIABLES + "." + VARIABLE_VALUE, outlierParams.getVariableTerm()));
    mainFilterQuery.must(nestedQuery(VARIABLES, variableTermFilterQuery, ScoreMode.None));

    final Integer recordLimit = configurationService.getExportCsvLimit();
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(mainFilterQuery)
      .fetchSource(PROCESS_INSTANCE_ID, null)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(recordLimit > MAX_RESPONSE_SIZE_LIMIT ? MAX_RESPONSE_SIZE_LIMIT : recordLimit);

    final SearchRequest scrollSearchRequest =
      new SearchRequest(getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()))
        .source(searchSourceBuilder)
        .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest);
      return ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
        response,
        ProcessInstanceIdDto.class,
        objectMapper,
        esClient,
        configurationService.getEsScrollTimeoutInSeconds(),
        recordLimit
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not obtain outlier instance ids.", e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to obtain outlier instance IDs because instance index with name {} does not exist. " +
            "Returning empty list.",
          getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey())
        );
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private BoolQueryBuilder createFlowNodeOutlierQuery(final FlowNodeOutlierParametersDto outlierParams) {
    final BoolQueryBuilder flowNodeFilterQuery = boolQuery()
      .must(termQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, outlierParams.getFlowNodeId()))
      .minimumShouldMatch(1);
    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
        rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).gt(outlierParams.getHigherOutlierBound())
      );
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
        rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).lt(outlierParams.getLowerOutlierBound())
      );
    }
    return flowNodeFilterQuery;
  }

  private ParsedReverseNested getVariableTermOccurrencesOfNonOutliers(final FlowNodeOutlierParametersDto outlierParams,
                                                                      final Map<String, Set<String>> outlierVariableTerms)
    throws IOException {
    final SearchRequest nonOutliersTermOccurrencesRequest = createTopVariableTermsOfNonOutliersQuery(
      outlierParams, outlierVariableTerms
    );
    return extractNestedProcessInstanceAgg(
      esClient.search(nonOutliersTermOccurrencesRequest)
    );

  }

  private ParsedReverseNested getTopVariableTermsOfOutliers(final FlowNodeOutlierParametersDto outlierParams)
    throws IOException {
    final List<String> variableNames = processVariableReader.getVariableNames(
      new ProcessVariableNameRequestDto(
        outlierParams.getProcessDefinitionKey(),
        outlierParams.getProcessDefinitionVersions(),
        outlierParams.getTenantIds()
      )
    ).stream().map(ProcessVariableNameResponseDto::getName).collect(Collectors.toList());

    final SearchRequest outlierTopVariableTermsRequest = createTopVariableTermsOfOutliersQuery(
      outlierParams, variableNames
    );
    return extractNestedProcessInstanceAgg(esClient.search(outlierTopVariableTermsRequest));
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
    final BoolQueryBuilder flowNodeFilterQuery = createFlowNodeOutlierQuery(outlierParams);

    final NestedAggregationBuilder nestedVariableAggregation =
      AggregationBuilders.nested(AGG_VARIABLES, VARIABLES);
    variableNames.stream().distinct().forEach(variableName -> nestedVariableAggregation.subAggregation(
      AggregationBuilders.filter(variableName, termQuery(VARIABLES + "." + VARIABLE_NAME, variableName))
        .subAggregation(
          AggregationBuilders.terms(AGG_VARIABLE_VALUE_TERMS).field(VARIABLES + "." + VARIABLE_VALUE)
            // This corresponds to the min doc count also used by elasticsearch's own significant terms implementation
            // and serves the purpose to ignore high cardinality values
            // @formatter:off
            // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-significantterms-aggregation.html
            // @formatter:on
            .minDocCount(3)
        )
    ));

    return createFilteredFlowNodeVariableAggregation(outlierParams, flowNodeFilterQuery, nestedVariableAggregation);
  }

  private SearchRequest createTopVariableTermsOfNonOutliersQuery(final FlowNodeOutlierParametersDto outlierParams,
                                                                 final Map<String, Set<String>> variablesAndTerms) {
    final BoolQueryBuilder flowNodeFilterQuery = boolQuery()
      .must(termQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, outlierParams.getFlowNodeId()))
      .minimumShouldMatch(1);

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
        rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).lte(outlierParams.getHigherOutlierBound())
      );
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
        rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).gte(outlierParams.getLowerOutlierBound())
      );
    }

    final NestedAggregationBuilder nestedVariableAggregation = AggregationBuilders.nested(
      AGG_VARIABLES,
      VARIABLES
    );
    variablesAndTerms
      .forEach((variableName, value) -> nestedVariableAggregation.subAggregation(
        AggregationBuilders.filter(
          variableName, termQuery(VARIABLES + "." + VARIABLE_NAME, variableName)
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
    final NestedAggregationBuilder nestedFlowNodeAggregation = AggregationBuilders.nested(
      FLOW_NODE_INSTANCES,
      FLOW_NODE_INSTANCES
    )
      .subAggregation(flowNodeFilterAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(createProcessDefinitionFilterQuery(outlierParams))
      .fetchSource(false)
      .aggregation(nestedFlowNodeAggregation)
      .size(0);

    return new SearchRequest(getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()))
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
              // The test returns true if the null hypothesis (both datasets originate from the same distribution)
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

      if (!variableValueTerms.getBuckets().isEmpty()) {
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

  private Map<String, FindingsDto> createFlowNodeOutlierMap(final List<? extends Terms.Bucket> deviationForEachFlowNode,
                                                            final BoolQueryBuilder processInstanceQuery,
                                                            final ProcessDefinitionParametersDto processDefinitionParams) {
    final Map<String, ExtendedStats> statsByFlowNodeId = new HashMap<>();
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .size(0);
    final NestedAggregationBuilder nestedFlowNodeAggregation = AggregationBuilders.nested(
      FLOW_NODE_INSTANCES,
      FLOW_NODE_INSTANCES
    );
    searchSourceBuilder.aggregation(nestedFlowNodeAggregation);
    deviationForEachFlowNode
      .forEach(bucket -> {
        final String flowNodeId = String.valueOf(bucket.getKeyAsString());
        final ExtendedStats statsAgg = bucket.getAggregations().get(AGG_STATS);
        statsByFlowNodeId.put(flowNodeId, statsAgg);

        if (statsAgg.getStdDeviation() != 0.0D) {
          double stdDeviationBoundLower = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.LOWER);
          double stdDeviationBoundHigher = statsAgg.getStdDeviationBound(ExtendedStats.Bounds.UPPER);
          double average = statsAgg.getAvg();
          stdDeviationBoundLower = Math.min(stdDeviationBoundLower,
                                            average - processDefinitionParams.getMinimumDeviationFromAvg());
          stdDeviationBoundHigher = Math.max(stdDeviationBoundHigher,
                                             average + processDefinitionParams.getMinimumDeviationFromAvg());
          final FilterAggregationBuilder lowerOutlierEventFilter = AggregationBuilders.filter(
            LOWER_DURATION_AGG,
            rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).lte(stdDeviationBoundLower)
          );

          final FilterAggregationBuilder higherOutlierEventFilter = AggregationBuilders.filter(
            HIGHER_DURATION_AGG,
            rangeQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION).gte(stdDeviationBoundHigher)
          );

          final TermQueryBuilder terms = termQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, flowNodeId);
          final FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(
            getFilteredFlowNodeAggregationName(flowNodeId), terms
          );
          filteredFlowNodes.subAggregation(lowerOutlierEventFilter);
          filteredFlowNodes.subAggregation(higherOutlierEventFilter);
          nestedFlowNodeAggregation.subAggregation(filteredFlowNodes);
        }
      });

    final SearchRequest searchRequest = new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionParams.getProcessDefinitionKey()))
      .source(searchSourceBuilder);
    try {
      final Aggregations allFlowNodesPercentileRanks = esClient.search(searchRequest)
        .getAggregations();
      final Aggregations allFlowNodeFilterAggs = ((Nested) allFlowNodesPercentileRanks.get(FLOW_NODE_INSTANCES)).getAggregations();
      return mapToFlowNodeFindingsMap(statsByFlowNodeId, allFlowNodeFilterAggs);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to retrieve flownode outlier map because instance index with alias {} does not exist. " +
            "Returning empty map.",
          getProcessInstanceIndexAliasName(processDefinitionParams.getProcessDefinitionKey())
        );
        return Collections.emptyMap();
      }
      throw e;
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
        finding.setTotalCount(stats.getCount());

        if (stats.getStdDeviation() != 0.0D
          && allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId)) != null) {
          final Filter flowNodeFilterAgg = allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId));
          final Filter lowerOutlierFilterAgg = flowNodeFilterAgg.getAggregations().get(LOWER_DURATION_AGG);
          final Filter higherOutlierFilterAgg = flowNodeFilterAgg.getAggregations().get(HIGHER_DURATION_AGG);

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
      .filter(entry -> entry.getValue().getOutlierCount() > 0)
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
    return Optional.ofNullable(lowerOutlierBound).map(value -> durationValue < value).orElse(false)
      || Optional.ofNullable(higherOutlierBound).map(value -> durationValue > value).orElse(false);
  }

  private long getInterval(final BoolQueryBuilder query, final String flowNodeId, final String processDefinitionKey) {
    StatsAggregationBuilder statsAgg = AggregationBuilders.stats(AGG_STATS)
      .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION);

    NestedAggregationBuilder termsAgg = buildNestedFlowNodeFilterAggregation(flowNodeId, statsAgg);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(termsAgg)
      .size(0);

    SearchRequest searchRequest =
      new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionKey)).source(searchSourceBuilder);

    SearchResponse search;
    try {
      search = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to determine interval because instance index {} does not exist. Returning 0.",
          getProcessInstanceIndexAliasName(processDefinitionKey)
        );
        return 0L;
      }
      throw e;
    }

    double min = ((Stats) ((Filter) ((Nested) search.getAggregations()
      .get(FLOW_NODE_INSTANCES)).getAggregations().get(AGG_FILTERED_FLOW_NODES)).getAggregations()
      .get(AGG_STATS)).getMin();
    double max = ((Stats) ((Filter) ((Nested) search.getAggregations()
      .get(FLOW_NODE_INSTANCES)).getAggregations().get(AGG_FILTERED_FLOW_NODES)).getAggregations()
      .get(AGG_STATS)).getMax();

    if (max == min) {
      // in case there is no distribution fallback to an interval of 1 as 0 is not a valid interval
      return 1L;
    } else {
      return (long) Math.ceil((max - min) / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    }
  }

  private NestedAggregationBuilder buildNestedFlowNodeFilterAggregation(final String flowNodeId,
                                                                        final AggregationBuilder subAggregation) {
    TermQueryBuilder terms = termQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID, flowNodeId);

    FilterAggregationBuilder filteredFlowNodes = AggregationBuilders.filter(AGG_FILTERED_FLOW_NODES, terms);
    filteredFlowNodes.subAggregation(subAggregation);

    return AggregationBuilders.nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES).subAggregation(filteredFlowNodes);
  }

  private ParsedReverseNested extractNestedProcessInstanceAgg(final SearchResponse outlierTopVariableTermsResponse) {
    return ((HasAggregations) ((HasAggregations) outlierTopVariableTermsResponse.getAggregations()
      .get(FLOW_NODE_INSTANCES)).getAggregations().get(AGG_FILTERED_FLOW_NODES))
      .getAggregations()
      .get(AGG_REVERSE_NESTED_PROCESS_INSTANCE);
  }

  private BoolQueryBuilder createProcessDefinitionFilterQuery(final ProcessDefinitionParametersDto processDefinitionParams) {
    return DefinitionQueryUtil.createDefinitionQuery(
      processDefinitionParams.getProcessDefinitionKey(),
      processDefinitionParams.getProcessDefinitionVersions(),
      processDefinitionParams.getTenantIds(),
      new ProcessInstanceIndex(processDefinitionParams.getProcessDefinitionKey()),
      processDefinitionReader::getLatestVersionToKey
    );
  }

  private double getRatio(final long totalCount, final long observedCount) {
    return (double) observedCount / totalCount;
  }

  private static List<String> generateListOfHumanTasks() {
    return List.of(TASK_USER_TASK, TASK_MANUAL_TASK);
  }

  private static List<String> generateListOfStandardExcludedFlowNodeTypes() {
    /* This list contains all the node types that we always want to exclude because they add no value to the outlier
    analysis. Please note that non-user task nodes that do add value to the analysis (e.g. service tasks) shall not
    be included in this list, as they shall also undergo an outlier analysis.
     */
    return List.of(
      GATEWAY_EXCLUSIVE, GATEWAY_INCLUSIVE, GATEWAY_PARALLEL, GATEWAY_COMPLEX, GATEWAY_EVENT_BASED, CALL_ACTIVITY,
      BOUNDARY_TIMER, BOUNDARY_MESSAGE, BOUNDARY_SIGNAL, BOUNDARY_COMPENSATION, BOUNDARY_ERROR, BOUNDARY_ESCALATION,
      BOUNDARY_CANCEL, BOUNDARY_CONDITIONAL, START_EVENT, START_EVENT_TIMER, START_EVENT_MESSAGE, START_EVENT_SIGNAL,
      START_EVENT_ESCALATION, START_EVENT_COMPENSATION, START_EVENT_ERROR, START_EVENT_CONDITIONAL,
      INTERMEDIATE_EVENT_CATCH, INTERMEDIATE_EVENT_MESSAGE, INTERMEDIATE_EVENT_TIMER, INTERMEDIATE_EVENT_LINK,
      INTERMEDIATE_EVENT_SIGNAL,INTERMEDIATE_EVENT_CONDITIONAL, INTERMEDIATE_EVENT_THROW,
      INTERMEDIATE_EVENT_SIGNAL_THROW, INTERMEDIATE_EVENT_COMPENSATION_THROW, INTERMEDIATE_EVENT_MESSAGE_THROW,
      INTERMEDIATE_EVENT_NONE_THROW , INTERMEDIATE_EVENT_ESCALATION_THROW, END_EVENT_ERROR, END_EVENT_CANCEL,
      END_EVENT_TERMINATE, END_EVENT_MESSAGE, END_EVENT_SIGNAL, END_EVENT_COMPENSATION , END_EVENT_ESCALATION,
      END_EVENT_NONE);
  }
}
