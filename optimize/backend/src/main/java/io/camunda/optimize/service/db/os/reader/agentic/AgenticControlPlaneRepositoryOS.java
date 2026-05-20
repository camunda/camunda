/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader.agentic;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScript;
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

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.agentic.SummaryResult;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import io.camunda.optimize.service.agentic.DateIntervalResolver;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.builders.OptimizeSearchRequestOS;
import io.camunda.optimize.service.db.os.filter.agentic.AgentBaselineFilterBuilderOS;
import io.camunda.optimize.service.db.reader.agentic.AgenticControlPlaneRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.PercentilesAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TDigestPercentilesAggregate;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
@NullMarked
public class AgenticControlPlaneRepositoryOS implements AgenticControlPlaneRepository {

  private static final Logger LOG = LoggerFactory.getLogger(AgenticControlPlaneRepositoryOS.class);

  private static final String AGG_TOTAL_RUNS = "totalRuns";
  private static final String AGG_AVG_DURATION = "avgDuration";
  private static final String AGG_DURATION_PCT = "durationPct";
  private static final String AGG_TOTAL_INPUT = "totalInputTokens";
  private static final String AGG_TOTAL_OUTPUT = "totalOutputTokens";
  private static final String AGG_MEDIAN_TOKENS = "medianTokens";
  private static final String AGG_HAS_INCIDENTS = "hasIncidents";
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

  private final OptimizeOpenSearchClient osClient;

  public AgenticControlPlaneRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public SummaryResult getSummary(final AgentQueryParams params) {
    final List<Query> filters = AgentBaselineFilterBuilderOS.build(params);
    final Query rootQuery = Query.of(q -> q.bool(b -> b.filter(filters)));

    final Aggregation durationPctAgg =
        new Aggregation.Builder().percentiles(p -> p.field(DURATION).percents(50.0, 95.0)).build();

    final Aggregation medianTokensAgg =
        new Aggregation.Builder()
            .percentiles(
                new PercentilesAggregation.Builder()
                    .script(createDefaultScript(MEDIAN_TOKENS_SCRIPT))
                    .percents(50.0)
                    .build())
            .build();

    final Aggregation hasIncidentsAgg =
        new Aggregation.Builder()
            .filter(nested(INCIDENTS, exists(INCIDENTS + "." + INCIDENT_ID), ChildScoreMode.None))
            .build();

    final var searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(
                        osClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                    .query(rootQuery)
                    .size(0)
                    .aggregations(
                        AGG_TOTAL_RUNS, a -> a.valueCount(v -> v.field(PROCESS_INSTANCE_ID)))
                    .aggregations(AGG_AVG_DURATION, a -> a.avg(v -> v.field(DURATION)))
                    .aggregations(AGG_DURATION_PCT, durationPctAgg)
                    .aggregations(
                        AGG_TOTAL_INPUT, a -> a.sum(v -> v.field(AGENT_TOTAL_INPUT_TOKENS)))
                    .aggregations(
                        AGG_TOTAL_OUTPUT, a -> a.sum(v -> v.field(AGENT_TOTAL_OUTPUT_TOKENS)))
                    .aggregations(AGG_MEDIAN_TOKENS, medianTokensAgg)
                    .aggregations(AGG_HAS_INCIDENTS, hasIncidentsAgg));

    final SearchResponse<Object> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve agentic summary", e);
      return emptySummaryResult();
    }

    return mapSummaryResult(response.aggregations());
  }

  @Override
  public ProcessBreakdownResponse getProcessBreakdown(final AgentQueryParams params) {
    final List<Query> filters = AgentBaselineFilterBuilderOS.build(params);
    final Query rootQuery = Query.of(q -> q.bool(b -> b.filter(filters)));

    final Aggregation byProcessAgg =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.field(PROCESS_DEFINITION_KEY)
                        .size(500)
                        .order(Map.of(AGG_TOTAL_TOKENS, SortOrder.Desc)))
            .aggregations(
                AGG_TOTAL_INPUT, Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_INPUT_TOKENS))))
            .aggregations(
                AGG_TOTAL_OUTPUT,
                Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_OUTPUT_TOKENS))))
            .aggregations(
                AGG_TOTAL_TOKENS,
                Aggregation.of(a -> a.sum(s -> s.script(createDefaultScript(TOTAL_TOKENS_SCRIPT)))))
            .aggregations(
                AGG_COUNT, Aggregation.of(a -> a.valueCount(v -> v.field(PROCESS_INSTANCE_ID))))
            .build();

    final var searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(osClient, getProcessInstanceIndexAliasName(null))
                    .query(rootQuery)
                    .size(0)
                    .aggregations(AGG_BY_PROCESS, byProcessAgg));

    final SearchResponse<Object> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve agentic process breakdown", e);
      return new ProcessBreakdownResponse(List.of());
    }

    return mapBreakdown(response.aggregations());
  }

  @Override
  public TrendsResponse getTrends(final AgentQueryParams params) {
    final String interval =
        DateIntervalResolver.resolve(params.startDateFrom(), params.startDateTo());
    final List<Query> filters = AgentBaselineFilterBuilderOS.build(params);
    final Query rootQuery = Query.of(q -> q.bool(b -> b.filter(filters)));

    final Aggregation tokenPctAgg =
        new Aggregation.Builder()
            .percentiles(
                new PercentilesAggregation.Builder()
                    .script(createDefaultScript(TOTAL_TOKENS_SCRIPT))
                    .percents(5.0, 50.0, 95.0)
                    .build())
            .build();

    final Aggregation durationPctAgg =
        new Aggregation.Builder().percentiles(p -> p.field(DURATION).percents(50.0, 95.0)).build();

    final Aggregation byDateAgg =
        new Aggregation.Builder()
            .dateHistogram(h -> h.field(END_DATE).calendarInterval(toCalendarInterval(interval)))
            .aggregations(
                AGG_TREND_INPUT, Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_INPUT_TOKENS))))
            .aggregations(
                AGG_TREND_OUTPUT,
                Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_OUTPUT_TOKENS))))
            .aggregations(AGG_TOKEN_PCT, tokenPctAgg)
            .aggregations(AGG_DURATION_PCT, durationPctAgg)
            .build();

    final var searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(
                        osClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                    .query(rootQuery)
                    .size(0)
                    .aggregations(AGG_BY_DATE, byDateAgg));

    final SearchResponse<Object> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve agentic trends", e);
      return new TrendsResponse(interval, List.of());
    }

    return mapTrends(response.aggregations(), interval);
  }

  @Override
  public ChartsResponse getCharts(final AgentQueryParams params) {
    final boolean isL1 = params.processDefinitionKey() != null;
    final List<Query> filters = AgentBaselineFilterBuilderOS.build(params);
    final Query rootQuery = Query.of(q -> q.bool(b -> b.filter(filters)));

    final Aggregation toolFreqAgg =
        new Aggregation.Builder()
            .nested(n -> n.path(AGENT_INSTANCES))
            .aggregations(
                AGG_TOOL_TERMS,
                Aggregation.of(
                    a ->
                        a.terms(
                            t ->
                                t.field(AGENT_INSTANCES + "." + AGENT_INSTANCE_TOOL_NAME)
                                    .size(50))))
            .build();

    final Aggregation byProcessTokensAgg =
        new Aggregation.Builder()
            .terms(t -> t.field(PROCESS_DEFINITION_KEY).size(500))
            .aggregations(
                AGG_SUM_INPUT, Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_INPUT_TOKENS))))
            .aggregations(
                AGG_SUM_OUTPUT, Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_OUTPUT_TOKENS))))
            .aggregations(
                AGG_SUM_MODEL_CALLS,
                Aggregation.of(a -> a.sum(s -> s.field(AGENT_TOTAL_MODEL_CALLS))))
            .build();

    final Aggregation byVersionAgg =
        new Aggregation.Builder()
            .terms(t -> t.field(PROCESS_DEFINITION_VERSION).size(50))
            .aggregations(
                AGG_VERSION_INCIDENTS,
                Aggregation.of(
                    a ->
                        a.filter(
                            nested(
                                INCIDENTS,
                                exists(INCIDENTS + "." + INCIDENT_ID),
                                ChildScoreMode.None))))
            .build();

    final var searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(
                        osClient, getProcessInstanceIndexAliasName(params.processDefinitionKey()))
                    .query(rootQuery)
                    .size(0)
                    .aggregations(AGG_TOOL_FREQ, toolFreqAgg)
                    .aggregations(AGG_BY_PROCESS_TOKENS, byProcessTokensAgg)
                    .aggregations(AGG_BY_VERSION, byVersionAgg));

    final SearchResponse<Object> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve agentic charts", e);
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
            .map(b -> new ChartsResponse.ToolFrequencyItem(b.key(), b.docCount()))
            .toList();

    final List<ChartsResponse.AvgTokensItem> avgTokensPerCall =
        requireNonNull(aggs.get(AGG_BY_PROCESS_TOKENS)).sterms().buckets().array().stream()
            .map(
                b -> {
                  final long input =
                      doubleAggToLong(
                          requireNonNull(b.aggregations().get(AGG_SUM_INPUT)).sum().value());
                  final long output =
                      doubleAggToLong(
                          requireNonNull(b.aggregations().get(AGG_SUM_OUTPUT)).sum().value());
                  final long modelCalls =
                      doubleAggToLong(
                          requireNonNull(b.aggregations().get(AGG_SUM_MODEL_CALLS)).sum().value());
                  final Double avg = modelCalls > 0 ? (double) (input + output) / modelCalls : null;
                  return new ChartsResponse.AvgTokensItem(b.key(), avg, modelCalls);
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
                    final int version = Integer.parseInt(b.key());
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
    final var tokenPctAgg =
        requireNonNull(bucket.aggregations().get(AGG_TOKEN_PCT)).tdigestPercentiles();
    final var durationPctAgg =
        requireNonNull(bucket.aggregations().get(AGG_DURATION_PCT)).tdigestPercentiles();
    return new TrendsResponse.TrendPoint(
        Instant.ofEpochMilli(bucket.key()).toString(),
        doubleAggToLong(requireNonNull(bucket.aggregations().get(AGG_TREND_INPUT)).sum().value()),
        doubleAggToLong(requireNonNull(bucket.aggregations().get(AGG_TREND_OUTPUT)).sum().value()),
        parsePercentile(tokenPctAgg, "5.0"),
        parsePercentile(tokenPctAgg, "50.0"),
        parsePercentile(tokenPctAgg, "95.0"),
        parsePercentile(durationPctAgg, "50.0"),
        parsePercentile(durationPctAgg, "95.0"));
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
        bucket.key(),
        doubleAggToLong(requireNonNull(bucket.aggregations().get(AGG_TOTAL_INPUT)).sum().value()),
        doubleAggToLong(requireNonNull(bucket.aggregations().get(AGG_TOTAL_OUTPUT)).sum().value()),
        doubleAggToLong(requireNonNull(bucket.aggregations().get(AGG_COUNT)).valueCount().value()));
  }

  private SummaryResult mapSummaryResult(final Map<String, Aggregate> aggs) {
    final long totalRuns =
        doubleAggToLong(requireNonNull(aggs.get(AGG_TOTAL_RUNS)).valueCount().value());
    final long avgDurationMs =
        doubleAggToLong(requireNonNull(aggs.get(AGG_AVG_DURATION)).avg().value());

    final var durationPctAgg = requireNonNull(aggs.get(AGG_DURATION_PCT)).tdigestPercentiles();
    final long p50 = parsePercentile(durationPctAgg, "50.0");
    final long p95 = parsePercentile(durationPctAgg, "95.0");

    final long totalInput =
        doubleAggToLong(requireNonNull(aggs.get(AGG_TOTAL_INPUT)).sum().value());
    final long totalOutput =
        doubleAggToLong(requireNonNull(aggs.get(AGG_TOTAL_OUTPUT)).sum().value());

    final var medianPctAgg = requireNonNull(aggs.get(AGG_MEDIAN_TOKENS)).tdigestPercentiles();
    final long medianTokens = parsePercentile(medianPctAgg, "50.0");

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

  private static long parsePercentile(
      final TDigestPercentilesAggregate agg, final String percentileKey) {
    if (agg.values() == null) {
      return 0L;
    }
    final var entry = agg.values().keyed().get(percentileKey);
    if (entry == null) {
      return 0L;
    }
    final Double v = entry.to(Double.class);
    if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
      return 0L;
    }
    return v.longValue();
  }

  private static long doubleAggToLong(@Nullable final Double value) {
    if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
      return 0L;
    }
    return value.longValue();
  }

  @Override
  public Set<String> getProcessDefinitionKeysWithAgentRuns(final AgentQueryParams params) {
    final List<Query> filters = AgentBaselineFilterBuilderOS.build(params);
    final Query rootQuery = Query.of(q -> q.bool(b -> b.filter(filters)));

    final Aggregation processKeysAgg =
        new Aggregation.Builder().terms(t -> t.field(PROCESS_DEFINITION_KEY).size(10000)).build();

    final var searchRequest =
        OptimizeSearchRequestOS.of(
            s ->
                s.optimizeIndex(osClient, getProcessInstanceIndexAliasName(null))
                    .query(rootQuery)
                    .size(0)
                    .aggregations(AGG_PROCESS_KEYS, processKeysAgg));

    final SearchResponse<Object> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve process definition keys with agent runs", e);
      return Set.of();
    }

    final var termsAgg = requireNonNull(response.aggregations().get(AGG_PROCESS_KEYS)).sterms();
    final Set<String> keys = new HashSet<>();
    for (final StringTermsBucket bucket : termsAgg.buckets().array()) {
      keys.add(bucket.key());
    }
    return keys;
  }

  private static SummaryResult emptySummaryResult() {
    return new SummaryResult(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }
}
