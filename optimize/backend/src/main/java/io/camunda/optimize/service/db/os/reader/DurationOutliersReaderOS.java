/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
<<<<<<< HEAD
=======
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessVariableReader;
import io.camunda.optimize.service.db.util.AggregationNameUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
<<<<<<< HEAD
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
=======
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.ExtendedStatsAggregate;
import org.opensearch.client.opensearch._types.aggregations.ExtendedStatsAggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DurationOutliersReaderOS implements DurationOutliersReader {

  @Override
  public List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto>
          outlierAnalysisParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
<<<<<<< HEAD
    log.debug("Functionality not implemented for OpenSearch");
    return null;
=======
    final FlowNodeOutlierParametersDto outlierParams =
        outlierAnalysisParams.getProcessDefinitionParametersDto();
    if (outlierParams.getLowerOutlierBound() == null
        && outlierParams.getHigherOutlierBound() == null) {
      throw new OptimizeValidationException(
          "One of lowerOutlierBound or higherOutlierBound must be set.");
    }

    try {
      // #1 get top variable value terms of outliers
      final List<String> variableNames = getVariableNames(outlierParams);
      final Map<String, String> sanitisedNameToVarName =
          getVariableNames(outlierParams).stream()
              .filter(AggregationNameUtil::containsIllegalChar)
              .collect(Collectors.toMap(AggregationNameUtil::sanitiseAggName, Function.identity()));
      final ReverseNestedAggregate outlierNestedProcessInstancesAgg =
          getTopVariableTermsOfOutliers(outlierAnalysisParams, variableNames);
      final Map<String, Map<String, Long>> outlierVariableTermOccurrences =
          createVariableTermOccurrencesMap(
              outlierNestedProcessInstancesAgg.aggregations().get(AGG_VARIABLES).nested(),
              sanitisedNameToVarName);
      final long outlierProcessInstanceCount = outlierNestedProcessInstancesAgg.docCount();
      final Map<String, Set<String>> outlierVariableTerms =
          outlierVariableTermOccurrences.entrySet().stream()
              .map(
                  variableAndTerms ->
                      new AbstractMap.SimpleEntry<>(
                          variableAndTerms.getKey(), variableAndTerms.getValue().keySet()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      if (outlierProcessInstanceCount == 0) {
        return new ArrayList<>();
      }

      // #2 get counts of the same terms from non outlier instances
      final ReverseNestedAggregate nonOutlierNestedProcessInstancesAgg =
          getVariableTermOccurrencesOfNonOutliers(outlierAnalysisParams, outlierVariableTerms);
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence =
          createVariableTermOccurrencesMap(
              nonOutlierNestedProcessInstancesAgg.aggregations().get(AGG_VARIABLES).nested(),
              sanitisedNameToVarName);
      final long nonOutlierProcessInstanceCount = nonOutlierNestedProcessInstancesAgg.docCount();

      // #3 compare both data sets and only keep terms whose frequency is considered significant
      final long totalProcessInstanceCount =
          outlierProcessInstanceCount + nonOutlierProcessInstanceCount;
      final Map<String, Map<String, Long>> outlierSignificantVariableTerms =
          filterSignificantOutlierVariableTerms(
              outlierVariableTermOccurrences,
              nonOutlierVariableTermOccurrence,
              outlierProcessInstanceCount,
              nonOutlierProcessInstanceCount);

      return mapToVariableTermList(
          outlierSignificantVariableTerms,
          nonOutlierVariableTermOccurrence,
          outlierProcessInstanceCount,
          nonOutlierProcessInstanceCount,
          totalProcessInstanceCount);

    } catch (final IOException e) {
      LOG.warn("Couldn't determine significant outlier variable terms.");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to determine significant outlier variable terms because instance index with name {} does not "
                + "exist. Returning empty list.",
            LogUtil.sanitizeLogMessage(
                getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey())));
        return Collections.emptyList();
      }
      throw e;
    }
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)
  }

  @Override
  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
<<<<<<< HEAD
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> outlierParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
=======
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto>
          outlierParamsDto) {
    final FlowNodeOutlierVariableParametersDto flowNodeOutlierVariableParams =
        outlierParamsDto.getProcessDefinitionParametersDto();
    // filter by definition
    final BoolQuery.Builder mainFilterQuery = buildBaseQuery(outlierParamsDto);
    // flowNode id & outlier duration
    final BoolQuery.Builder flowNodeFilterQuery = createFlowNodeOutlierQuery(outlierParamsDto);
    mainFilterQuery.must(
        m ->
            m.nested(
                n ->
                    n.path(FLOW_NODE_INSTANCES)
                        .query(q -> q.bool(flowNodeFilterQuery.build()))
                        .scoreMode(ChildScoreMode.None)));
    // variable name & term
    final BoolQuery.Builder variableTermFilterQuery = new BoolQuery.Builder();
    variableTermFilterQuery.must(
        m ->
            m.term(
                t ->
                    t.field(VARIABLES + "." + VARIABLE_NAME)
                        .value(FieldValue.of(flowNodeOutlierVariableParams.getVariableName()))));
    variableTermFilterQuery.must(
        m ->
            m.term(
                t ->
                    t.field(VARIABLES + "." + VARIABLE_VALUE)
                        .value(FieldValue.of(flowNodeOutlierVariableParams.getVariableTerm()))));
    mainFilterQuery.must(
        m ->
            m.nested(
                n ->
                    n.path(VARIABLES)
                        .query(q -> q.bool(variableTermFilterQuery.build()))
                        .scoreMode(ChildScoreMode.None)));

    final Integer recordLimit = configurationService.getCsvConfiguration().getExportCsvLimit();

    final SearchRequest scrollSearchRequest =
        OptimizeSearchRequestOS.of(
            b ->
                b.optimizeIndex(
                        osClient,
                        getProcessInstanceIndexAliasName(
                            flowNodeOutlierVariableParams.getProcessDefinitionKey()))
                    .query(q -> q.bool(mainFilterQuery.build()))
                    .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_ID)))
                    // size of each scroll page, needs to be capped to max size of opensearch
                    .size(
                        recordLimit > MAX_RESPONSE_SIZE_LIMIT
                            ? MAX_RESPONSE_SIZE_LIMIT
                            : recordLimit)
                    .scroll(
                        s ->
                            s.time(
                                configurationService
                                        .getOpenSearchConfiguration()
                                        .getScrollTimeoutInSeconds()
                                    + "s")));

    try {
      final List<Hit<ProcessInstanceIdDto>> hits = new ArrayList<>();
      final SearchResponse<ProcessInstanceIdDto> response =
          osClient.searchUnsafe(scrollSearchRequest, ProcessInstanceIdDto.class);
      osClient.scrollWith(response, hits::addAll, ProcessInstanceIdDto.class, recordLimit);
      return hits.stream().map(Hit::source).toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not obtain outlier instance ids.", e);
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to obtain outlier instance IDs because instance index with name {} does not exist. "
                + "Returning empty list.",
            LogUtil.sanitizeLogMessage(
                getProcessInstanceIndexAliasName(
                    flowNodeOutlierVariableParams.getProcessDefinitionKey())));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private <T extends FlowNodeOutlierParametersDto> BoolQuery.Builder createFlowNodeOutlierQuery(
      final OutlierAnalysisServiceParameters<T> outlierParameters) {
    final T outlierParams = outlierParameters.getProcessDefinitionParametersDto();
    final BoolQuery.Builder flowNodeFilterQuery = new BoolQuery.Builder();
    flowNodeFilterQuery
        .must(
            m ->
                m.term(
                    t ->
                        t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                            .value(FieldValue.of(outlierParams.getFlowNodeId()))))
        .minimumShouldMatch("1");

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.gt(JsonData.of(outlierParams.getHigherOutlierBound()))
                          .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)));
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.lt(JsonData.of(outlierParams.getLowerOutlierBound()))
                          .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)));
    }
    flowNodeFilterQuery.filter(getQueryFilters(outlierParams, outlierParameters.getZoneId()));
    return flowNodeFilterQuery;
  }

  private List<Query> getQueryFilters(
      final ProcessDefinitionParametersDto params, final ZoneId zoneId) {
    return queryFilterEnhancer.filterQueries(
        params.getFilters(), FilterContext.builder().timezone(zoneId).build());
  }

  private ReverseNestedAggregate getVariableTermOccurrencesOfNonOutliers(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierParams,
      final Map<String, Set<String>> outlierVariableTerms)
      throws IOException {
    final SearchRequest nonOutliersTermOccurrencesRequest =
        createTopVariableTermsOfNonOutliersQuery(outlierParams, outlierVariableTerms);
    return extractNestedProcessInstanceAgg(
        osClient.searchUnsafe(nonOutliersTermOccurrencesRequest, Object.class));
  }

  private ReverseNestedAggregate getTopVariableTermsOfOutliers(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams,
      final List<String> variableNames)
      throws IOException {
    final SearchRequest outlierTopVariableTermsRequest =
        createTopVariableTermsOfOutliersQuery(outlierAnalysisParams, variableNames);
    return extractNestedProcessInstanceAgg(
        osClient.searchUnsafe(outlierTopVariableTermsRequest, Object.class));
  }

  private List<String> getVariableNames(final FlowNodeOutlierParametersDto outlierParams) {
    final List<String> variableNames =
        processVariableReader
            .getVariableNames(
                new ProcessVariableNameRequestDto(
                    List.of(
                        new ProcessToQueryDto(
                            outlierParams.getProcessDefinitionKey(),
                            outlierParams.getProcessDefinitionVersions(),
                            outlierParams.getTenantIds()))))
            .stream()
            .map(ProcessVariableNameResponseDto::getName)
            .collect(Collectors.toList());
    return variableNames;
  }

  private SearchRequest createTopVariableTermsOfOutliersQuery(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierParams,
      final List<String> variableNames) {
    final BoolQuery.Builder flowNodeFilterQuery = createFlowNodeOutlierQuery(outlierParams);

    final Aggregation nestedVariableAggregation =
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder nested = a.nested(n -> n.path(VARIABLES));
              variableNames.stream()
                  .distinct()
                  .forEach(
                      variableName ->
                          nested.aggregations(
                              AggregationNameUtil.sanitiseAggName(variableName),
                              Aggregation.of(
                                  aa ->
                                      aa.filter(
                                              f ->
                                                  f.term(
                                                      t ->
                                                          t.field(VARIABLES + "." + VARIABLE_NAME)
                                                              .value(FieldValue.of(variableName))))
                                          .aggregations(
                                              AGG_VARIABLE_VALUE_TERMS,
                                              Aggregation.of(
                                                  aaa ->
                                                      aaa.terms(
                                                          tt ->
                                                              tt.field(
                                                                      VARIABLES
                                                                          + "."
                                                                          + VARIABLE_VALUE)
                                                                  .minDocCount(3)))))));
              return nested;
            });

    return createFilteredFlowNodeVariableAggregation(
        outlierParams, flowNodeFilterQuery.build(), AGG_VARIABLES, nestedVariableAggregation);
  }

  private SearchRequest createTopVariableTermsOfNonOutliersQuery(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierParameters,
      final Map<String, Set<String>> variablesAndTerms) {
    final FlowNodeOutlierParametersDto outlierParams =
        outlierParameters.getProcessDefinitionParametersDto();

    final BoolQuery.Builder flowNodeFilterQuery = new BoolQuery.Builder();
    flowNodeFilterQuery
        .must(
            m ->
                m.term(
                    t ->
                        t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                            .value(FieldValue.of(outlierParams.getFlowNodeId()))))
        .minimumShouldMatch("1");

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
                          .lte(JsonData.of(outlierParams.getHigherOutlierBound()))));
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
                          .gte(JsonData.of(outlierParams.getLowerOutlierBound()))));
    }
    final Aggregation nestedVariableAggregation =
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder nested = a.nested(n -> n.path(VARIABLES));
              variablesAndTerms.forEach(
                  (variableName, value) ->
                      nested.aggregations(
                          variableName,
                          Aggregation.of(
                              aa ->
                                  aa.filter(
                                          f ->
                                              f.term(
                                                  t ->
                                                      t.field(VARIABLES + "." + VARIABLE_NAME)
                                                          .value(FieldValue.of(variableName))))
                                      .aggregations(
                                          AGG_VARIABLE_VALUE_TERMS,
                                          Aggregation.of(
                                              aaa ->
                                                  aaa.terms(
                                                      t ->
                                                          t.field(VARIABLES + "." + VARIABLE_VALUE)
                                                              .include(
                                                                  tt ->
                                                                      tt.terms(
                                                                          value.stream()
                                                                              .toList()))))))));
              return nested;
            });

    return createFilteredFlowNodeVariableAggregation(
        outlierParameters, flowNodeFilterQuery.build(), AGG_VARIABLES, nestedVariableAggregation);
  }

  private SearchRequest createFilteredFlowNodeVariableAggregation(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierParams,
      final BoolQuery flowNodeFilterQuery,
      final String aggName,
      final Aggregation nestedVariableAggregation) {
    final Aggregation flowNodeFilterAggregation =
        Aggregation.of(
            a ->
                a.filter(f -> f.bool(flowNodeFilterQuery))
                    .aggregations(
                        AGG_REVERSE_NESTED_PROCESS_INSTANCE,
                        Aggregation.of(
                            aa ->
                                aa.reverseNested(r -> r)
                                    .aggregations(aggName, nestedVariableAggregation))));
    final Aggregation nestedFlowNodeAggregation =
        Aggregation.of(
            a ->
                a.nested(n -> n.path(FLOW_NODE_INSTANCES))
                    .aggregations(AGG_FILTERED_FLOW_NODES, flowNodeFilterAggregation));

    return OptimizeSearchRequestOS.of(
        b ->
            b.optimizeIndex(
                    osClient,
                    getProcessInstanceIndexAliasName(
                        outlierParams
                            .getProcessDefinitionParametersDto()
                            .getProcessDefinitionKey()))
                .query(q -> q.bool(buildBaseQuery(outlierParams).build()))
                .source(s -> s.fetch(false))
                .aggregations(FLOW_NODE_INSTANCES, nestedFlowNodeAggregation)
                .size(0));
  }

  private Map<String, Map<String, Long>> createVariableTermOccurrencesMap(
      final NestedAggregate allVariableAggregations,
      final Map<String, String> sanitisedVarsToVarNames) {
    final Map<String, Map<String, Long>> outlierVariableTermOccurrences = new HashMap<>();
    allVariableAggregations
        .aggregations()
        .forEach(
            (variableName, aggregation) -> {
              final FilterAggregate variableFilterAggregation = aggregation.filter();
              final StringTermsAggregate variableValueTerms =
                  variableFilterAggregation.aggregations().get(AGG_VARIABLE_VALUE_TERMS).sterms();

              if (!variableValueTerms.buckets().array().isEmpty()) {
                final Map<String, Long> termOccurrences =
                    variableValueTerms.buckets().array().stream()
                        .map(
                            bucket ->
                                new AbstractMap.SimpleEntry<>(bucket.key(), bucket.docCount()))
                        .collect(
                            Collectors.toMap(
                                AbstractMap.SimpleEntry::getKey,
                                AbstractMap.SimpleEntry::getValue));

                // We resolve this back to its original name in the case that it was sanitised
                // during query time
                outlierVariableTermOccurrences.put(
                    Optional.ofNullable(sanitisedVarsToVarNames.get(variableName))
                        .orElse(variableName),
                    termOccurrences);
              }
            });
    return outlierVariableTermOccurrences;
  }

  private Map<String, FindingsDto> createFlowNodeOutlierMap(
      final List<StringTermsBucket> deviationForEachFlowNode,
      final BoolQuery processInstanceQuery,
      final ProcessDefinitionParametersDto processDefinitionParams) {
    final Map<String, ExtendedStatsAggregate> statsByFlowNodeId = new HashMap<>();
    final Aggregation nestedFlowNodeAggregation =
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder nested =
                  a.nested(n -> n.path(FLOW_NODE_INSTANCES));
              deviationForEachFlowNode.forEach(
                  bucket -> {
                    final String flowNodeId = bucket.key();
                    final ExtendedStatsAggregate statsAgg =
                        bucket.aggregations().get(AGG_STATS).extendedStats();
                    statsByFlowNodeId.put(flowNodeId, statsAgg);

                    if (statsAgg.stdDeviation() != 0.0D) {
                      double stdDeviationBoundLower = statsAgg.stdDeviationBounds().lower();
                      double stdDeviationBoundHigher = statsAgg.stdDeviationBounds().upper();
                      final double average = statsAgg.avg();
                      stdDeviationBoundLower =
                          Math.min(
                              stdDeviationBoundLower,
                              average - processDefinitionParams.getMinimumDeviationFromAvg());
                      stdDeviationBoundHigher =
                          Math.max(
                              stdDeviationBoundHigher,
                              average + processDefinitionParams.getMinimumDeviationFromAvg());

                      final double finalStdDeviationBoundLower = stdDeviationBoundLower;
                      final Aggregation lowerOutlierEventFilter =
                          Aggregation.of(
                              aa ->
                                  aa.filter(
                                      f ->
                                          f.range(
                                              r ->
                                                  r.field(
                                                          FLOW_NODE_INSTANCES
                                                              + "."
                                                              + FLOW_NODE_TOTAL_DURATION)
                                                      .lte(
                                                          JsonData.of(
                                                              finalStdDeviationBoundLower)))));

                      final double finalStdDeviationBoundHigher = stdDeviationBoundHigher;
                      final Aggregation higherOutlierEventFilter =
                          Aggregation.of(
                              aa ->
                                  aa.filter(
                                      f ->
                                          f.range(
                                              r ->
                                                  r.field(
                                                          FLOW_NODE_INSTANCES
                                                              + "."
                                                              + FLOW_NODE_TOTAL_DURATION)
                                                      .gte(
                                                          JsonData.of(
                                                              finalStdDeviationBoundHigher)))));

                      final TermQuery terms =
                          TermQuery.of(
                              t ->
                                  t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                                      .value(FieldValue.of(flowNodeId)));
                      final Aggregation filteredFlowNodes =
                          Aggregation.of(
                              aa ->
                                  aa.filter(f -> f.term(terms))
                                      .aggregations(LOWER_DURATION_AGG, lowerOutlierEventFilter)
                                      .aggregations(HIGHER_DURATION_AGG, higherOutlierEventFilter));

                      nested.aggregations(
                          getFilteredFlowNodeAggregationName(flowNodeId), filteredFlowNodes);
                    }
                  });
              return nested;
            });

    final SearchRequest searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(
                        osClient,
                        getProcessInstanceIndexAliasName(
                            processDefinitionParams.getProcessDefinitionKey()))
                    .query(q -> q.bool(processInstanceQuery))
                    .source(o -> o.fetch(false))
                    .size(0)
                    .aggregations(FLOW_NODE_INSTANCES, nestedFlowNodeAggregation));
    try {
      final Map<String, Aggregate> allFlowNodesPercentileRanks =
          osClient.searchUnsafe(searchRequest, Object.class).aggregations();
      final Map<String, Aggregate> allFlowNodeFilterAggs =
          allFlowNodesPercentileRanks.get(FLOW_NODE_INSTANCES).nested().aggregations();
      return mapToFlowNodeFindingsMap(statsByFlowNodeId, allFlowNodeFilterAggs);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to retrieve flownode outlier map because instance index with alias {} does not exist. "
                + "Returning empty map.",
            LogUtil.sanitizeLogMessage(
                getProcessInstanceIndexAliasName(
                    processDefinitionParams.getProcessDefinitionKey())));
        return Collections.emptyMap();
      }
      throw e;
    }
  }

  private Map<String, FindingsDto> mapToFlowNodeFindingsMap(
      final Map<String, ExtendedStatsAggregate> statsByFlowNodeId,
      final Map<String, Aggregate> allFlowNodeFilterAggs) {
    final AtomicLong totalLowerOutlierCount = new AtomicLong(0L);
    final AtomicLong totalHigherOutlierCount = new AtomicLong(0L);
    final Map<String, FindingsDto> findingsDtoMap =
        statsByFlowNodeId.entrySet().stream()
            .map(
                flowNodeStatsEntry -> {
                  final String flowNodeId = flowNodeStatsEntry.getKey();
                  final ExtendedStatsAggregate stats = flowNodeStatsEntry.getValue();
                  final FindingsDto finding = new FindingsDto();
                  finding.setTotalCount(stats.count());

                  if (stats.stdDeviation() != 0.0D
                      && allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId))
                          != null) {
                    final Aggregate flowNodeFilterAgg =
                        allFlowNodeFilterAggs.get(getFilteredFlowNodeAggregationName(flowNodeId));
                    final Aggregate lowerOutlierFilterAgg =
                        flowNodeFilterAgg.filter().aggregations().get(LOWER_DURATION_AGG);
                    final Aggregate higherOutlierFilterAgg =
                        flowNodeFilterAgg.filter().aggregations().get(HIGHER_DURATION_AGG);

                    final double avg = stats.avg();
                    final double stdDeviationBoundLower = stats.stdDeviationBounds().lower();
                    final double stdDeviationBoundHigher = stats.stdDeviationBounds().upper();

                    if (stdDeviationBoundLower > stats.min()
                        && lowerOutlierFilterAgg.filter().docCount() > 0L) {
                      final long count = lowerOutlierFilterAgg.filter().docCount();
                      final double percent = (double) count / flowNodeFilterAgg.filter().docCount();
                      finding.setLowerOutlier(
                          (long) stdDeviationBoundLower,
                          percent,
                          avg / stdDeviationBoundLower,
                          count);
                      totalLowerOutlierCount.addAndGet(count);
                    }

                    if (stdDeviationBoundHigher < stats.max()
                        && higherOutlierFilterAgg.filter().docCount() > 0) {
                      final long count = higherOutlierFilterAgg.filter().docCount();
                      final double percent = (double) count / flowNodeFilterAgg.filter().docCount();
                      finding.setHigherOutlier(
                          (long) stdDeviationBoundHigher,
                          percent,
                          stdDeviationBoundHigher / avg,
                          count);
                      totalHigherOutlierCount.addAndGet(count);
                    }
                  }

                  return new AbstractMap.SimpleEntry<>(flowNodeId, finding);
                })
            .filter(entry -> entry.getValue().getOutlierCount() > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    final long totalOutlierCount = totalLowerOutlierCount.get() + totalHigherOutlierCount.get();
    findingsDtoMap
        .values()
        .forEach(
            finding -> {
              finding
                  .getLowerOutlier()
                  .ifPresent(
                      lowerOutlier ->
                          finding.setLowerOutlierHeat(
                              getRatio(totalLowerOutlierCount.get(), lowerOutlier.getCount())));
              finding
                  .getHigherOutlier()
                  .ifPresent(
                      higherOutlier ->
                          finding.setHigherOutlierHeat(
                              getRatio(totalHigherOutlierCount.get(), higherOutlier.getCount())));
              finding.setHeat(getRatio(totalOutlierCount, finding.getOutlierCount()));
            });
    return findingsDtoMap;
  }

  private long getInterval(
      final BoolQuery query, final String flowNodeId, final String processDefinitionKey) {
    final StatsAggregation statsAgg =
        StatsAggregation.of(s -> s.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION));

    final Aggregation termsAgg =
        buildNestedFlowNodeFilterAggregation(
            flowNodeId, AGG_STATS, Aggregation.of(a -> a.stats(statsAgg)));

    final SearchRequest searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(osClient, getProcessInstanceIndexAliasName(processDefinitionKey))
                    .query(q -> q.bool(query))
                    .source(o -> o.fetch(false))
                    .aggregations(FLOW_NODE_INSTANCES, termsAgg)
                    .size(0));

    final SearchResponse<?> search;
    try {
      search = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to determine interval because instance index {} does not exist. Returning 0.",
            LogUtil.sanitizeLogMessage(getProcessInstanceIndexAliasName(processDefinitionKey)));
        return 0L;
      }
      throw e;
    }

    final StatsAggregate stats =
        search
            .aggregations()
            .get(FLOW_NODE_INSTANCES)
            .nested()
            .aggregations()
            .get(AGG_FILTERED_FLOW_NODES)
            .filter()
            .aggregations()
            .get(AGG_STATS)
            .stats();
    final double min = stats.min();
    final double max = stats.max();

    if (max == min || stats.count() == 0) {
      // in case there is no distribution fallback to an interval of 1 as 0 is not a valid interval
      return 1L;
    } else {
      return (long)
          Math.ceil((max - min) / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    }
  }

  private Aggregation buildNestedFlowNodeFilterAggregation(
      final String flowNodeId, final String agrKey, final Aggregation subAggregation) {
    final TermQuery.Builder terms = new TermQuery.Builder();
    terms.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID);
    terms.value(FieldValue.of(flowNodeId));

    final Aggregation filteredFlowNodes =
        Aggregation.of(
            a -> a.filter(f -> f.term(terms.build())).aggregations(agrKey, subAggregation));

    return Aggregation.of(
        a ->
            a.nested(n -> n.path(FLOW_NODE_INSTANCES))
                .aggregations(AGG_FILTERED_FLOW_NODES, filteredFlowNodes));
  }

  private ReverseNestedAggregate extractNestedProcessInstanceAgg(
      final SearchResponse<?> outlierTopVariableTermsResponse) {
    return outlierTopVariableTermsResponse
        .aggregations()
        .get(FLOW_NODE_INSTANCES)
        .nested()
        .aggregations()
        .get(AGG_FILTERED_FLOW_NODES)
        .filter()
        .aggregations()
        .get(AGG_REVERSE_NESTED_PROCESS_INSTANCE)
        .reverseNested();
  }

  private <T extends ProcessDefinitionParametersDto> BoolQuery.Builder buildBaseQuery(
      final OutlierAnalysisServiceParameters<T> outlierParams) {
    final T processDefinitionParams = outlierParams.getProcessDefinitionParametersDto();
    final BoolQuery.Builder definitionQuery =
        DefinitionQueryUtilOS.createDefinitionQuery(
            processDefinitionParams.getProcessDefinitionKey(),
            processDefinitionParams.getProcessDefinitionVersions(),
            processDefinitionParams.getTenantIds(),
            new ProcessInstanceIndexOS(processDefinitionParams.getProcessDefinitionKey()),
            processDefinitionReader::getLatestVersionToKey);
    definitionQuery.filter(getQueryFilters(processDefinitionParams, outlierParams.getZoneId()));
    return definitionQuery;
>>>>>>> 03df3535 (fix: sanitise aggregation names during outlier analysis to avoid illegal characters)
  }
}
