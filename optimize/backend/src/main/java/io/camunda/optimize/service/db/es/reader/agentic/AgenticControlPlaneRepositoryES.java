/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader.agentic;

import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_TOOL_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_TOTAL_INPUT_TOKENS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_TOTAL_MODEL_CALLS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_TOTAL_OUTPUT_TOKENS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static java.util.Objects.requireNonNull;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.agentic.SummaryResult;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import io.camunda.optimize.service.agentic.DateIntervalResolver;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.agentic.AgentBaselineFilterBuilderES;
import io.camunda.optimize.service.db.reader.agentic.AgenticControlPlaneRepository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
@NullMarked
public class AgenticControlPlaneRepositoryES implements AgenticControlPlaneRepository {

  private static final Logger LOG = LoggerFactory.getLogger(AgenticControlPlaneRepositoryES.class);

  // summary agg names
  private static final String AGG_TOTAL_RUNS = "totalRuns";
  private static final String AGG_AVG_DURATION = "avgDuration";
  private static final String AGG_DURATION_PCT = "durationPct";
  private static final String AGG_TOTAL_INPUT = "totalInputTokens";
  private static final String AGG_TOTAL_OUTPUT = "totalOutputTokens";
  private static final String AGG_MEDIAN_TOKENS = "medianTokens";
  private static final String AGG_HAS_INCIDENTS = "hasIncidents";

  // process-breakdown agg names
  private static final String AGG_BY_PROCESS = "byProcess";
  private static final String AGG_TOTAL_TOKENS = "totalTokens";
  private static final String AGG_COUNT = "processInstanceCount";

  // trends agg names
  private static final String AGG_BY_DATE = "byDate";
  private static final String AGG_TREND_INPUT = "trendInput";
  private static final String AGG_TREND_OUTPUT = "trendOutput";
  private static final String AGG_TOKEN_PCT = "tokenPct";

  // process-definitions agg names
  private static final String AGG_PROCESS_KEYS = "processKeys";

  // charts agg names
  private static final String AGG_TOOL_FREQ = "toolFreq";
  private static final String AGG_TOOL_TERMS = "toolTerms";
  private static final String AGG_BY_PROCESS_TOKENS = "byProcessForTokens";
  private static final String AGG_SUM_INPUT = "sumInput";
  private static final String AGG_SUM_OUTPUT = "sumOutput";
  private static final String AGG_SUM_MODEL_CALLS = "sumModelCalls";
  private static final String AGG_BY_VERSION = "byVersion";
  private static final String AGG_VERSION_INCIDENTS = "versionIncidents";

  private static final String MEDIAN_TOKENS_SCRIPT =
      "(doc['"
          + AGENT_TOTAL_INPUT_TOKENS
          + "'].size() > 0 ? doc['"
          + AGENT_TOTAL_INPUT_TOKENS
          + "'].value : 0L)"
          + " + "
          + "(doc['"
          + AGENT_TOTAL_OUTPUT_TOKENS
          + "'].size() > 0 ? doc['"
          + AGENT_TOTAL_OUTPUT_TOKENS
          + "'].value : 0L)";

  private static final String TOTAL_TOKENS_SCRIPT = MEDIAN_TOKENS_SCRIPT;

  private final OptimizeElasticsearchClient esClient;

  public AgenticControlPlaneRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public SummaryResult getSummary(final AgentQueryParams params) {
    final var boolQuery = AgentBaselineFilterBuilderES.build(params).build();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(
                        esClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                    .query(q -> q.bool(boolQuery))
                    .size(0)
                    .aggregations(
                        AGG_TOTAL_RUNS, a -> a.valueCount(v -> v.field(PROCESS_INSTANCE_ID)))
                    .aggregations(AGG_AVG_DURATION, a -> a.avg(v -> v.field(DURATION)))
                    .aggregations(
                        AGG_DURATION_PCT,
                        a -> a.percentiles(p -> p.field(DURATION).percents(50.0, 95.0)))
                    .aggregations(
                        AGG_TOTAL_INPUT, a -> a.sum(v -> v.field(AGENT_TOTAL_INPUT_TOKENS)))
                    .aggregations(
                        AGG_TOTAL_OUTPUT, a -> a.sum(v -> v.field(AGENT_TOTAL_OUTPUT_TOKENS)))
                    .aggregations(
                        AGG_MEDIAN_TOKENS,
                        a ->
                            a.percentiles(
                                p ->
                                    p.script(createDefaultScript(MEDIAN_TOKENS_SCRIPT))
                                        .percents(50.0)))
                    .aggregations(
                        AGG_HAS_INCIDENTS,
                        a ->
                            a.filter(
                                f ->
                                    f.nested(
                                        n ->
                                            n.path(INCIDENTS)
                                                .scoreMode(ChildScoreMode.None)
                                                .query(
                                                    q ->
                                                        q.exists(
                                                            e ->
                                                                e.field(
                                                                    INCIDENTS
                                                                        + "."
                                                                        + INCIDENT_ID)))))));

    final SearchResponse<Object> response;
    try {
      response = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.warn("Failed to retrieve agentic summary", e);
      return emptySummaryResult();
    } catch (final ElasticsearchException e) {
      LOG.info(
          "Agentic summary query failed — index {} may not exist yet",
          getProcessInstanceIndexAliasName(params.processDefinitionKey()));
      return emptySummaryResult();
    }

    return mapSummaryResult(response.aggregations());
  }

  @Override
  public ProcessBreakdownResponse getProcessBreakdown(final AgentQueryParams params) {
    final var boolQuery = AgentBaselineFilterBuilderES.build(params).build();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, getProcessInstanceIndexAliasName(null))
                    .query(q -> q.bool(boolQuery))
                    .size(0)
                    .aggregations(
                        AGG_BY_PROCESS,
                        a ->
                            a.terms(
                                    t ->
                                        t.field(PROCESS_DEFINITION_KEY)
                                            .size(500)
                                            .order(
                                                List.of(
                                                    NamedValue.of(
                                                        AGG_TOTAL_TOKENS, SortOrder.Desc))))
                                .aggregations(
                                    AGG_TOTAL_INPUT,
                                    sa -> sa.sum(v -> v.field(AGENT_TOTAL_INPUT_TOKENS)))
                                .aggregations(
                                    AGG_TOTAL_OUTPUT,
                                    sa -> sa.sum(v -> v.field(AGENT_TOTAL_OUTPUT_TOKENS)))
                                .aggregations(
                                    AGG_TOTAL_TOKENS,
                                    sa ->
                                        sa.sum(
                                            v ->
                                                v.script(createDefaultScript(TOTAL_TOKENS_SCRIPT))))
                                .aggregations(
                                    AGG_COUNT,
                                    sa -> sa.valueCount(v -> v.field(PROCESS_INSTANCE_ID)))));

    final SearchResponse<Object> response;
    try {
      response = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.warn("Failed to retrieve agentic process breakdown", e);
      return new ProcessBreakdownResponse(List.of());
    } catch (final ElasticsearchException e) {
      LOG.info("Agentic process breakdown query failed — index may not exist yet");
      return new ProcessBreakdownResponse(List.of());
    }

    return mapBreakdown(response.aggregations());
  }

  @Override
  public TrendsResponse getTrends(final AgentQueryParams params) {
    final String interval =
        DateIntervalResolver.resolve(params.startDateFrom(), params.startDateTo());
    final var boolQuery = AgentBaselineFilterBuilderES.build(params).build();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(
                        esClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                    .query(q -> q.bool(boolQuery))
                    .size(0)
                    .aggregations(
                        AGG_BY_DATE,
                        a ->
                            a.dateHistogram(
                                    h ->
                                        h.field(END_DATE)
                                            .calendarInterval(toCalendarInterval(interval)))
                                .aggregations(
                                    AGG_TREND_INPUT,
                                    sa -> sa.sum(v -> v.field(AGENT_TOTAL_INPUT_TOKENS)))
                                .aggregations(
                                    AGG_TREND_OUTPUT,
                                    sa -> sa.sum(v -> v.field(AGENT_TOTAL_OUTPUT_TOKENS)))
                                .aggregations(
                                    AGG_TOKEN_PCT,
                                    sa ->
                                        sa.percentiles(
                                            p ->
                                                p.script(createDefaultScript(TOTAL_TOKENS_SCRIPT))
                                                    .percents(5.0, 50.0, 95.0)))
                                .aggregations(
                                    AGG_DURATION_PCT,
                                    sa ->
                                        sa.percentiles(
                                            p -> p.field(DURATION).percents(50.0, 95.0)))));

    final SearchResponse<Object> response;
    try {
      response = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.warn("Failed to retrieve agentic trends", e);
      return new TrendsResponse(interval, List.of());
    } catch (final ElasticsearchException e) {
      LOG.info("Agentic trends query failed — index may not exist yet");
      return new TrendsResponse(interval, List.of());
    }

    return mapTrends(response.aggregations(), interval);
  }

  @Override
  public ChartsResponse getCharts(final AgentQueryParams params) {
    final boolean isL1 = params.processDefinitionKey() != null;
    final var boolQuery = AgentBaselineFilterBuilderES.build(params).build();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s -> {
              s.optimizeIndex(
                      esClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                  .query(q -> q.bool(boolQuery))
                  .size(0)
                  .aggregations(
                      AGG_TOOL_FREQ,
                      a ->
                          a.nested(n -> n.path(AGENT_INSTANCES))
                              .aggregations(
                                  AGG_TOOL_TERMS,
                                  sa ->
                                      sa.terms(
                                          t ->
                                              t.field(
                                                      AGENT_INSTANCES
                                                          + "."
                                                          + AGENT_INSTANCE_TOOL_NAME)
                                                  .size(50))))
                  .aggregations(
                      AGG_BY_PROCESS_TOKENS,
                      a ->
                          a.terms(t -> t.field(PROCESS_DEFINITION_KEY).size(500))
                              .aggregations(
                                  AGG_SUM_INPUT,
                                  sa -> sa.sum(v -> v.field(AGENT_TOTAL_INPUT_TOKENS)))
                              .aggregations(
                                  AGG_SUM_OUTPUT,
                                  sa -> sa.sum(v -> v.field(AGENT_TOTAL_OUTPUT_TOKENS)))
                              .aggregations(
                                  AGG_SUM_MODEL_CALLS,
                                  sa -> sa.sum(v -> v.field(AGENT_TOTAL_MODEL_CALLS))));
              if (isL1) {
                s.aggregations(
                    AGG_BY_VERSION,
                    a ->
                        a.terms(t -> t.field(PROCESS_DEFINITION_VERSION).size(50))
                            .aggregations(
                                AGG_VERSION_INCIDENTS,
                                sa ->
                                    sa.filter(
                                        f ->
                                            f.nested(
                                                n ->
                                                    n.path(INCIDENTS)
                                                        .scoreMode(ChildScoreMode.None)
                                                        .query(
                                                            q ->
                                                                q.exists(
                                                                    e ->
                                                                        e.field(
                                                                            INCIDENTS
                                                                                + "."
                                                                                + INCIDENT_ID)))))));
              }
              return s;
            });

    final SearchResponse<Object> response;
    try {
      response = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.warn("Failed to retrieve agentic charts", e);
      return new ChartsResponse(List.of(), List.of(), null);
    } catch (final ElasticsearchException e) {
      LOG.info(
          "Agentic charts query failed — index {} may not exist yet",
          getProcessInstanceIndexAliasName(params.processDefinitionKey()));
      return new ChartsResponse(List.of(), List.of(), null);
    }

    return mapCharts(response.aggregations(), isL1);
  }

  private ChartsResponse mapCharts(final Map<String, Aggregate> aggs, final boolean isL1) {
    final List<ChartsResponse.ToolFrequencyItem> toolFrequency =
        requireNonNull(
                requireNonNull(aggs.get(AGG_TOOL_FREQ)).nested().aggregations().get(AGG_TOOL_TERMS))
            .sterms()
            .buckets()
            .array()
            .stream()
            .map(b -> new ChartsResponse.ToolFrequencyItem(b.key().stringValue(), b.docCount()))
            .toList();

    final List<ChartsResponse.AvgTokensItem> avgTokensPerCall =
        requireNonNull(aggs.get(AGG_BY_PROCESS_TOKENS)).sterms().buckets().array().stream()
            .map(
                b -> {
                  final long input =
                      (long) requireNonNull(b.aggregations().get(AGG_SUM_INPUT)).sum().value();
                  final long output =
                      (long) requireNonNull(b.aggregations().get(AGG_SUM_OUTPUT)).sum().value();
                  final long modelCalls =
                      (long)
                          requireNonNull(b.aggregations().get(AGG_SUM_MODEL_CALLS)).sum().value();
                  final Double avg = modelCalls > 0 ? (double) (input + output) / modelCalls : null;
                  return new ChartsResponse.AvgTokensItem(b.key().stringValue(), avg, modelCalls);
                })
            .toList();

    final List<ChartsResponse.VersionIncidentItem> incidentRateByVersion;
    if (isL1) {
      incidentRateByVersion =
          requireNonNull(aggs.get(AGG_BY_VERSION)).sterms().buckets().array().stream()
              .map(
                  b -> {
                    final long totalRuns = b.docCount();
                    final long incidents =
                        requireNonNull(b.aggregations().get(AGG_VERSION_INCIDENTS))
                            .filter()
                            .docCount();
                    final double rate = totalRuns > 0 ? (double) incidents / totalRuns : 0.0;
                    final int version = Integer.parseInt(b.key().stringValue());
                    return new ChartsResponse.VersionIncidentItem(version, rate, totalRuns);
                  })
              .toList();
    } else {
      incidentRateByVersion = null;
    }

    return new ChartsResponse(toolFrequency, avgTokensPerCall, incidentRateByVersion);
  }

  private TrendsResponse mapTrends(final Map<String, Aggregate> aggs, final String interval) {
    final var trend =
        requireNonNull(aggs.get(AGG_BY_DATE)).dateHistogram().buckets().array().stream()
            .map(this::mapTrendPoint)
            .toList();
    return new TrendsResponse(interval, trend);
  }

  private TrendsResponse.TrendPoint mapTrendPoint(final DateHistogramBucket bucket) {
    final var tokenPct =
        requireNonNull(bucket.aggregations().get(AGG_TOKEN_PCT))
            .tdigestPercentiles()
            .values()
            .keyed();
    final var durationPct =
        requireNonNull(bucket.aggregations().get(AGG_DURATION_PCT))
            .tdigestPercentiles()
            .values()
            .keyed();
    return new TrendsResponse.TrendPoint(
        Instant.ofEpochMilli(bucket.key()).toString(),
        (long) requireNonNull(bucket.aggregations().get(AGG_TREND_INPUT)).sum().value(),
        (long) requireNonNull(bucket.aggregations().get(AGG_TREND_OUTPUT)).sum().value(),
        parsePercentile(tokenPct, "5.0"),
        parsePercentile(tokenPct, "50.0"),
        parsePercentile(tokenPct, "95.0"),
        parsePercentile(durationPct, "50.0"),
        parsePercentile(durationPct, "95.0"));
  }

  private ProcessBreakdownResponse mapBreakdown(final Map<String, Aggregate> aggs) {
    final var items =
        requireNonNull(aggs.get(AGG_BY_PROCESS)).sterms().buckets().array().stream()
            .map(this::mapProcessItem)
            .toList();
    return new ProcessBreakdownResponse(items);
  }

  private ProcessBreakdownResponse.ProcessItem mapProcessItem(final StringTermsBucket bucket) {
    return new ProcessBreakdownResponse.ProcessItem(
        bucket.key().stringValue(),
        (long) requireNonNull(bucket.aggregations().get(AGG_TOTAL_INPUT)).sum().value(),
        (long) requireNonNull(bucket.aggregations().get(AGG_TOTAL_OUTPUT)).sum().value(),
        (long) requireNonNull(bucket.aggregations().get(AGG_COUNT)).valueCount().value());
  }

  private SummaryResult mapSummaryResult(final Map<String, Aggregate> aggs) {
    final long totalRuns = (long) requireNonNull(aggs.get(AGG_TOTAL_RUNS)).valueCount().value();
    final long avgDurationMs =
        doubleAggToLong(requireNonNull(aggs.get(AGG_AVG_DURATION)).avg().value());

    final var durationPct =
        requireNonNull(aggs.get(AGG_DURATION_PCT)).tdigestPercentiles().values().keyed();
    final long p50 = parsePercentile(durationPct, "50.0");
    final long p95 = parsePercentile(durationPct, "95.0");

    final long totalInput = (long) requireNonNull(aggs.get(AGG_TOTAL_INPUT)).sum().value();
    final long totalOutput = (long) requireNonNull(aggs.get(AGG_TOTAL_OUTPUT)).sum().value();

    final var medianPct =
        requireNonNull(aggs.get(AGG_MEDIAN_TOKENS)).tdigestPercentiles().values().keyed();
    final long medianTokens = parsePercentile(medianPct, "50.0");

    final long incidentCount = requireNonNull(aggs.get(AGG_HAS_INCIDENTS)).filter().docCount();

    return new SummaryResult(
        totalRuns, avgDurationMs, p50, p95, totalInput, totalOutput, medianTokens, incidentCount);
  }

  private static CalendarInterval toCalendarInterval(final String interval) {
    return switch (interval) {
      case "1h" -> CalendarInterval.Hour;
      case "1w" -> CalendarInterval.Week;
      case "1M" -> CalendarInterval.Month;
      default -> CalendarInterval.Day;
    };
  }

  private static long parsePercentile(final Map<String, String> keyed, final String percentileKey) {
    return Optional.ofNullable(keyed.get(percentileKey))
        .filter(v -> !v.equalsIgnoreCase("NaN") && !v.equalsIgnoreCase("Infinity"))
        .map(Double::parseDouble)
        .map(Double::longValue)
        .orElse(0L);
  }

  private static long doubleAggToLong(final Double value) {
    if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
      return 0L;
    }
    return value.longValue();
  }

  @Override
  public Set<String> getProcessDefinitionKeysWithAgentRuns(final AgentQueryParams params) {
    final var boolQuery = AgentBaselineFilterBuilderES.build(params).build();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, getProcessInstanceIndexAliasName(null))
                    .query(q -> q.bool(boolQuery))
                    .size(0)
                    .aggregations(
                        AGG_PROCESS_KEYS,
                        a -> a.terms(t -> t.field(PROCESS_DEFINITION_KEY).size(10000))));

    final SearchResponse<Object> response;
    try {
      response = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.warn("Failed to retrieve process definition keys with agent runs", e);
      return Set.of();
    } catch (final ElasticsearchException e) {
      LOG.info("Process definition keys query failed — index may not exist yet");
      return Set.of();
    }

    final var termsAgg = requireNonNull(response.aggregations().get(AGG_PROCESS_KEYS)).sterms();
    final Set<String> keys = new HashSet<>();
    for (final StringTermsBucket bucket : termsAgg.buckets().array()) {
      keys.add(bucket.key().stringValue());
    }
    return keys;
  }

  private static SummaryResult emptySummaryResult() {
    return new SummaryResult(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }
}
