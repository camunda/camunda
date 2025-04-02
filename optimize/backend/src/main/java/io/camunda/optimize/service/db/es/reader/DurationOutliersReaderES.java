/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
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

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ExtendedStatsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.ExtendedStatsAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.ReverseNestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessVariableReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.ZoneId;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DurationOutliersReaderES implements DurationOutliersReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessVariableReader processVariableReader;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ConfigurationService configurationService;

  @Override
  public List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    final BoolQuery query = buildBaseQuery(outlierAnalysisParams).build();

    final FlowNodeOutlierParametersDto outlierParams =
        outlierAnalysisParams.getProcessDefinitionParametersDto();
    final long interval =
        getInterval(query, outlierParams.getFlowNodeId(), outlierParams.getProcessDefinitionKey());

    final Aggregation histogram =
        Aggregation.of(
            a ->
                a.histogram(
                    h ->
                        h.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
                            .interval((double) interval)));

    final Aggregation nestedAgg =
        buildNestedFlowNodeFilterAggregation(
            outlierParams.getFlowNodeId(), AGG_HISTOGRAM, histogram);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(
                        esClient,
                        getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()))
                    .query(q -> q.bool(query))
                    .source(o -> o.fetch(false))
                    .aggregations(FLOW_NODE_INSTANCES, nestedAgg)
                    .size(0));

    final SearchResponse<DurationChartEntryDto> search;
    try {
      search = esClient.search(searchRequest, DurationChartEntryDto.class);
    } catch (final IOException e) {
      log.warn("Couldn't retrieve duration chart");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to evaluate count by duration chart because instance index with alias {} does not exist. "
                + "Returning empty list.",
            getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()));
        return Collections.emptyList();
      }
      throw e;
    }

    return search
        .aggregations()
        .get(FLOW_NODE_INSTANCES)
        .nested()
        .aggregations()
        .get(AGG_FILTERED_FLOW_NODES)
        .filter()
        .aggregations()
        .get(AGG_HISTOGRAM)
        .histogram()
        .buckets()
        .array()
        .stream()
        .map(
            b -> {
              try {
                final Long durationKey = Double.valueOf(b.key()).longValue();
                return new DurationChartEntryDto(
                    durationKey,
                    b.docCount(),
                    isOutlier(
                        outlierParams.getLowerOutlierBound(),
                        outlierParams.getHigherOutlierBound(),
                        durationKey));
              } catch (final NumberFormatException exception) {
                throw new OptimizeRuntimeException(
                    "Error mapping key to numerical value: " + b.keyAsString());
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto>
          outlierAnalysisParams) {
    final BoolQuery.Builder processInstanceQuery = buildBaseQuery(outlierAnalysisParams);

    final ExtendedStatsAggregation extendedStatsAggregation =
        ExtendedStatsAggregation.of(
            e -> e.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION));

    final BoolQuery.Builder builder = new BoolQuery.Builder();
    final ProcessDefinitionParametersDto processDefinitionParametersDto =
        outlierAnalysisParams.getProcessDefinitionParametersDto();
    if (Boolean.TRUE.equals(processDefinitionParametersDto.getDisconsiderAutomatedTasks())) {
      builder.filter(
          f ->
              f.terms(
                  t ->
                      t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE)
                          .terms(
                              tt ->
                                  tt.value(
                                      generateListOfHumanTasks().stream()
                                          .map(FieldValue::of)
                                          .toList()))));
    } else {
      builder.filter(
          f ->
              f.bool(
                  b ->
                      b.mustNot(
                          m ->
                              m.terms(
                                  t ->
                                      t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE)
                                          .terms(
                                              tt ->
                                                  tt.value(
                                                      generateListOfStandardExcludedFlowNodeTypes()
                                                          .stream()
                                                          .map(FieldValue::of)
                                                          .toList()))))));
    }

    final Aggregation aggregationFlowNodeTypeAndId =
        Aggregation.of(
            a ->
                a.filter(f -> f.bool(builder.build()))
                    .aggregations(
                        FLOW_NODE_ID_AGG,
                        Aggregation.of(
                            aa ->
                                aa.terms(
                                        t ->
                                            t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                                                .size(
                                                    configurationService
                                                        .getElasticSearchConfiguration()
                                                        .getAggregationBucketLimit()))
                                    .aggregations(
                                        AGG_STATS,
                                        Aggregation.of(
                                            aaa -> aaa.extendedStats(extendedStatsAggregation))))));

    final Aggregation nested =
        Aggregation.of(
            a ->
                a.nested(n -> n.path(FLOW_NODE_INSTANCES))
                    .aggregations(FLOW_NODE_TYPE_FILTER, aggregationFlowNodeTypeAndId));

    final BoolQuery boolQuery = processInstanceQuery.build();
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            o ->
                o.optimizeIndex(
                        esClient,
                        getProcessInstanceIndexAliasName(
                            processDefinitionParametersDto.getProcessDefinitionKey()))
                    .query(q -> q.bool(boolQuery))
                    .source(s -> s.fetch(false))
                    .aggregations(AGG_NESTED, nested)
                    .size(0));

    final SearchResponse<?> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason = "Could not fetch data to generate Outlier Analysis Heatmap";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to get Flow Node outlier map because instance index with alias {} does not exist. "
                + "Returning empty results.",
            getProcessInstanceIndexAliasName(
                processDefinitionParametersDto.getProcessDefinitionKey()));
        return Collections.emptyMap();
      }
      throw e;
    }
    final List<StringTermsBucket> deviationForEachFlowNode =
        searchResponse
            .aggregations()
            .get(AGG_NESTED)
            .nested()
            .aggregations()
            .get(FLOW_NODE_TYPE_FILTER)
            .filter()
            .aggregations()
            .get(FLOW_NODE_ID_AGG)
            .sterms()
            .buckets()
            .array();

    return createFlowNodeOutlierMap(
        deviationForEachFlowNode, boolQuery, processDefinitionParametersDto);
  }

  @Override
  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    final FlowNodeOutlierParametersDto outlierParams =
        outlierAnalysisParams.getProcessDefinitionParametersDto();
    if (outlierParams.getLowerOutlierBound() == null
        && outlierParams.getHigherOutlierBound() == null) {
      throw new OptimizeValidationException(
          "One of lowerOutlierBound or higherOutlierBound must be set.");
    }

    try {
      // #1 get top variable value terms of outliers
      final ReverseNestedAggregate outlierNestedProcessInstancesAgg =
          getTopVariableTermsOfOutliers(outlierAnalysisParams);
      final Map<String, Map<String, Long>> outlierVariableTermOccurrences =
          createVariableTermOccurrencesMap(
              outlierNestedProcessInstancesAgg.aggregations().get(AGG_VARIABLES).nested());
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
              nonOutlierNestedProcessInstancesAgg.aggregations().get(AGG_VARIABLES).nested());
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
      log.warn("Couldn't determine significant outlier variable terms.");
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to determine significant outlier variable terms because instance index with name {} does not "
                + "exist. Returning empty list.",
            getProcessInstanceIndexAliasName(outlierParams.getProcessDefinitionKey()));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  @Override
  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
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
                        .value(flowNodeOutlierVariableParams.getVariableName())));
    variableTermFilterQuery.must(
        m ->
            m.term(
                t ->
                    t.field(VARIABLES + "." + VARIABLE_VALUE)
                        .value(flowNodeOutlierVariableParams.getVariableTerm())));
    mainFilterQuery.must(
        m ->
            m.nested(
                n ->
                    n.path(VARIABLES)
                        .query(q -> q.bool(variableTermFilterQuery.build()))
                        .scoreMode(ChildScoreMode.None)));

    final Integer recordLimit = configurationService.getCsvConfiguration().getExportCsvLimit();

    final SearchRequest scrollSearchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(
                        esClient,
                        getProcessInstanceIndexAliasName(
                            flowNodeOutlierVariableParams.getProcessDefinitionKey()))
                    .query(q -> q.bool(mainFilterQuery.build()))
                    .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_ID)))
                    // size of each scroll page, needs to be capped to max size of elasticsearch
                    .size(
                        recordLimit > MAX_RESPONSE_SIZE_LIMIT
                            ? MAX_RESPONSE_SIZE_LIMIT
                            : recordLimit)
                    .scroll(
                        s ->
                            s.time(
                                configurationService
                                        .getElasticSearchConfiguration()
                                        .getScrollTimeoutInSeconds()
                                    + "s")));

    try {
      final SearchResponse<ProcessInstanceIdDto> response =
          esClient.search(scrollSearchRequest, ProcessInstanceIdDto.class);
      return ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
          response,
          ProcessInstanceIdDto.class,
          objectMapper,
          esClient,
          configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
          recordLimit);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not obtain outlier instance ids.", e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to obtain outlier instance IDs because instance index with name {} does not exist. "
                + "Returning empty list.",
            getProcessInstanceIndexAliasName(
                flowNodeOutlierVariableParams.getProcessDefinitionKey()));
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
                            .value(outlierParams.getFlowNodeId())))
        .minimumShouldMatch("1");

    addFiltersToQuery(outlierParams, flowNodeFilterQuery, outlierParameters.getZoneId());
    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.number(
                          n ->
                              n.gt(Double.valueOf(outlierParams.getHigherOutlierBound()))
                                  .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION))));
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.number(
                          n ->
                              n.lt(Double.valueOf(outlierParams.getLowerOutlierBound()))
                                  .field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION))));
    }
    return flowNodeFilterQuery;
  }

  private void addFiltersToQuery(
      final ProcessDefinitionParametersDto params,
      final BoolQuery.Builder query,
      final ZoneId zoneId) {
    queryFilterEnhancer.addFilterToQuery(
        query, params.getFilters(), FilterContext.builder().timezone(zoneId).build());
  }

  private ReverseNestedAggregate getVariableTermOccurrencesOfNonOutliers(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierParams,
      final Map<String, Set<String>> outlierVariableTerms)
      throws IOException {
    final SearchRequest nonOutliersTermOccurrencesRequest =
        createTopVariableTermsOfNonOutliersQuery(outlierParams, outlierVariableTerms);
    return extractNestedProcessInstanceAgg(
        esClient.search(nonOutliersTermOccurrencesRequest, Object.class));
  }

  private ReverseNestedAggregate getTopVariableTermsOfOutliers(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams)
      throws IOException {
    final FlowNodeOutlierParametersDto outlierParams =
        outlierAnalysisParams.getProcessDefinitionParametersDto();

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

    final SearchRequest outlierTopVariableTermsRequest =
        createTopVariableTermsOfOutliersQuery(outlierAnalysisParams, variableNames);
    return extractNestedProcessInstanceAgg(
        esClient.search(outlierTopVariableTermsRequest, Object.class));
  }

  private List<VariableTermDto> mapToVariableTermList(
      final Map<String, Map<String, Long>> outlierSignificantVariableTerms,
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
      final long outlierProcessInstanceCount,
      final long nonOutlierProcessInstanceCount,
      final long totalProcessInstanceCount) {

    return outlierSignificantVariableTerms.entrySet().stream()
        .flatMap(
            significantVariableTerms ->
                significantVariableTerms.getValue().entrySet().stream()
                    .map(
                        termAndCount -> {
                          final String variableName = significantVariableTerms.getKey();
                          final Long outlierTermOccurrence = termAndCount.getValue();
                          return new VariableTermDto(
                              variableName,
                              termAndCount.getKey(),
                              outlierTermOccurrence,
                              getRatio(outlierProcessInstanceCount, outlierTermOccurrence),
                              Optional.ofNullable(
                                      nonOutlierVariableTermOccurrence.get(variableName))
                                  .flatMap(
                                      entry ->
                                          Optional.ofNullable(entry.get(termAndCount.getKey())))
                                  .map(
                                      nonOutlierTermOccurrence ->
                                          getRatio(
                                              nonOutlierProcessInstanceCount,
                                              nonOutlierTermOccurrence))
                                  .orElse(0.0D),
                              getRatio(totalProcessInstanceCount, outlierTermOccurrence));
                        }))
        .sorted(Comparator.comparing(VariableTermDto::getInstanceCount).reversed())
        .collect(Collectors.toList());
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
                              variableName,
                              Aggregation.of(
                                  aa ->
                                      aa.filter(
                                              f ->
                                                  f.term(
                                                      t ->
                                                          t.field(VARIABLES + "." + VARIABLE_NAME)
                                                              .value(variableName)))
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
                                                                  // This corresponds to the min doc
                                                                  // count also used by
                                                                  // elasticsearch's own significant
                                                                  // terms implementation
                                                                  // and serves the purpose to
                                                                  // ignore high cardinality values
                                                                  // @formatter:off
                                                                  // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-significantterms-aggregation.html
                                                                  // @formatter:on
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
                            .value(outlierParams.getFlowNodeId())))
        .minimumShouldMatch("1");

    if (outlierParams.getHigherOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.number(
                          n ->
                              n.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
                                  .lte(Double.valueOf(outlierParams.getHigherOutlierBound())))));
    }
    if (outlierParams.getLowerOutlierBound() != null) {
      flowNodeFilterQuery.should(
          s ->
              s.range(
                  r ->
                      r.number(
                          n ->
                              n.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION)
                                  .gte(Double.valueOf(outlierParams.getLowerOutlierBound())))));
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
                                                          .value(variableName)))
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

    return OptimizeSearchRequestBuilderES.of(
        b ->
            b.optimizeIndex(
                    esClient,
                    getProcessInstanceIndexAliasName(
                        outlierParams
                            .getProcessDefinitionParametersDto()
                            .getProcessDefinitionKey()))
                .query(q -> q.bool(buildBaseQuery(outlierParams).build()))
                .source(s -> s.fetch(false))
                .aggregations(FLOW_NODE_INSTANCES, nestedFlowNodeAggregation)
                .size(0));
  }

  private Map<String, Map<String, Long>> filterSignificantOutlierVariableTerms(
      final Map<String, Map<String, Long>> outlierVariableTermOccurrences,
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
      final long outlierProcessInstanceCount,
      final long nonOutlierProcessInstanceCount) {

    return outlierVariableTermOccurrences.entrySet().stream()
        .map(
            outlierVariableTermOccurrence -> {
              final String variableName = outlierVariableTermOccurrence.getKey();
              final Map<String, Long> outlierTermOccurrences =
                  outlierVariableTermOccurrence.getValue();
              final Map<String, Long> nonOutlierTermOccurrences =
                  nonOutlierVariableTermOccurrence.getOrDefault(
                      variableName, Collections.emptyMap());

              final Map<String, Long> significantTerms =
                  outlierTermOccurrences.entrySet().stream()
                      .filter(
                          outlierTermAndCount -> {
                            final String term = outlierTermAndCount.getKey();
                            final Long outlierTermCount = outlierTermAndCount.getValue();
                            final Long nonOutlierTermCount =
                                nonOutlierTermOccurrences.getOrDefault(term, 0L);

                            final boolean isMoreFrequentInOutlierSet =
                                getRatio(outlierProcessInstanceCount, outlierTermCount)
                                    > getRatio(nonOutlierProcessInstanceCount, nonOutlierTermCount);

                            final boolean isSignificant =
                                TestUtils.chiSquareTestDataSetsComparison(
                                    new long[] {
                                      nonOutlierTermCount, nonOutlierProcessInstanceCount
                                    },
                                    new long[] {outlierTermCount, outlierProcessInstanceCount},
                                    // This is the confidence level or alpha that defines the degree
                                    // of confidence of the test result.
                                    // The test returns true if the null hypothesis (both datasets
                                    // originate from the same distribution)
                                    // can be rejected with 100 * (1 - alpha) percent confidence and
                                    // thus the sets can be considered
                                    // to be significantly different
                                    0.001D);

                            return isMoreFrequentInOutlierSet && isSignificant;
                          })
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
              return new AbstractMap.SimpleEntry<>(variableName, significantTerms);
            })
        .filter(stringMapSimpleEntry -> !stringMapSimpleEntry.getValue().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, Map<String, Long>> createVariableTermOccurrencesMap(
      final NestedAggregate allVariableAggregations) {
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
                                new AbstractMap.SimpleEntry<>(
                                    bucket.key().stringValue(), bucket.docCount()))
                        .collect(
                            Collectors.toMap(
                                AbstractMap.SimpleEntry::getKey,
                                AbstractMap.SimpleEntry::getValue));

                outlierVariableTermOccurrences.put(variableName, termOccurrences);
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
                    final String flowNodeId = bucket.key().stringValue();
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
                                                  r.number(
                                                      n ->
                                                          n.field(
                                                                  FLOW_NODE_INSTANCES
                                                                      + "."
                                                                      + FLOW_NODE_TOTAL_DURATION)
                                                              .lte(finalStdDeviationBoundLower)))));

                      final double finalStdDeviationBoundHigher = stdDeviationBoundHigher;
                      final Aggregation higherOutlierEventFilter =
                          Aggregation.of(
                              aa ->
                                  aa.filter(
                                      f ->
                                          f.range(
                                              r ->
                                                  r.number(
                                                      n ->
                                                          n.field(
                                                                  FLOW_NODE_INSTANCES
                                                                      + "."
                                                                      + FLOW_NODE_TOTAL_DURATION)
                                                              .gte(
                                                                  finalStdDeviationBoundHigher)))));

                      final TermQuery terms =
                          TermQuery.of(
                              t ->
                                  t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                                      .value(flowNodeId));
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
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(
                        esClient,
                        getProcessInstanceIndexAliasName(
                            processDefinitionParams.getProcessDefinitionKey()))
                    .query(q -> q.bool(processInstanceQuery))
                    .source(o -> o.fetch(false))
                    .size(0)
                    .aggregations(FLOW_NODE_INSTANCES, nestedFlowNodeAggregation));
    try {
      final Map<String, Aggregate> allFlowNodesPercentileRanks =
          esClient.search(searchRequest, Object.class).aggregations();
      final Map<String, Aggregate> allFlowNodeFilterAggs =
          allFlowNodesPercentileRanks.get(FLOW_NODE_INSTANCES).nested().aggregations();
      return mapToFlowNodeFindingsMap(statsByFlowNodeId, allFlowNodeFilterAggs);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to retrieve flownode outlier map because instance index with alias {} does not exist. "
                + "Returning empty map.",
            getProcessInstanceIndexAliasName(processDefinitionParams.getProcessDefinitionKey()));
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

  private String getFilteredFlowNodeAggregationName(final String flowNodeId) {
    return AGG_FILTERED_FLOW_NODES + flowNodeId;
  }

  private boolean isOutlier(
      final Long lowerOutlierBound, final Long higherOutlierBound, final Long durationValue) {
    return Optional.ofNullable(lowerOutlierBound).map(value -> durationValue < value).orElse(false)
        || Optional.ofNullable(higherOutlierBound)
            .map(value -> durationValue > value)
            .orElse(false);
  }

  private long getInterval(
      final BoolQuery query, final String flowNodeId, final String processDefinitionKey) {
    final StatsAggregation statsAgg =
        StatsAggregation.of(s -> s.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION));

    final Aggregation termsAgg =
        buildNestedFlowNodeFilterAggregation(
            flowNodeId, AGG_STATS, Aggregation.of(a -> a.stats(statsAgg)));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, getProcessInstanceIndexAliasName(processDefinitionKey))
                    .query(q -> q.bool(query))
                    .source(o -> o.fetch(false))
                    .aggregations(FLOW_NODE_INSTANCES, termsAgg)
                    .size(0));

    final SearchResponse<?> search;
    try {
      search = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e.getMessage(), e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to determine interval because instance index {} does not exist. Returning 0.",
            getProcessInstanceIndexAliasName(processDefinitionKey));
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

    if ((max == min) || stats.count() == 0) {
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
    terms.value(flowNodeId);

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
        DefinitionQueryUtilES.createDefinitionQuery(
            processDefinitionParams.getProcessDefinitionKey(),
            processDefinitionParams.getProcessDefinitionVersions(),
            processDefinitionParams.getTenantIds(),
            new ProcessInstanceIndexES(processDefinitionParams.getProcessDefinitionKey()),
            processDefinitionReader::getLatestVersionToKey);
    addFiltersToQuery(processDefinitionParams, definitionQuery, outlierParams.getZoneId());
    return definitionQuery;
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
        GATEWAY_EXCLUSIVE,
        GATEWAY_INCLUSIVE,
        GATEWAY_PARALLEL,
        GATEWAY_COMPLEX,
        GATEWAY_EVENT_BASED,
        CALL_ACTIVITY,
        BOUNDARY_TIMER,
        BOUNDARY_MESSAGE,
        BOUNDARY_SIGNAL,
        BOUNDARY_COMPENSATION,
        BOUNDARY_ERROR,
        BOUNDARY_ESCALATION,
        BOUNDARY_CANCEL,
        BOUNDARY_CONDITIONAL,
        START_EVENT,
        START_EVENT_TIMER,
        START_EVENT_MESSAGE,
        START_EVENT_SIGNAL,
        START_EVENT_ESCALATION,
        START_EVENT_COMPENSATION,
        START_EVENT_ERROR,
        START_EVENT_CONDITIONAL,
        INTERMEDIATE_EVENT_CATCH,
        INTERMEDIATE_EVENT_MESSAGE,
        INTERMEDIATE_EVENT_TIMER,
        INTERMEDIATE_EVENT_LINK,
        INTERMEDIATE_EVENT_SIGNAL,
        INTERMEDIATE_EVENT_CONDITIONAL,
        INTERMEDIATE_EVENT_THROW,
        INTERMEDIATE_EVENT_SIGNAL_THROW,
        INTERMEDIATE_EVENT_COMPENSATION_THROW,
        INTERMEDIATE_EVENT_MESSAGE_THROW,
        INTERMEDIATE_EVENT_NONE_THROW,
        INTERMEDIATE_EVENT_ESCALATION_THROW,
        END_EVENT_ERROR,
        END_EVENT_CANCEL,
        END_EVENT_TERMINATE,
        END_EVENT_MESSAGE,
        END_EVENT_SIGNAL,
        END_EVENT_COMPENSATION,
        END_EVENT_ESCALATION,
        END_EVENT_NONE);
  }
}
