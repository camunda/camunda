/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.FailureReason;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun.ProcessViewAgentRunRateInterpreterES;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Queries and aggregations over the agent run index shared by the agentic dashboard's tables. Runs
 * are restricted the same way as the dashboard tiles: completed process instances, by the run's
 * process instance end date.
 */
final class AgentRunQueriesES {

  static final String RUNS = "runs";
  static final String FAILED = "failed";
  static final String LIMITS = "limits";
  static final String FAILURE_REASONS = "failureReasons";
  private static final String REASON_KINDS = "kinds";
  private static final String REASON_LIMITS = "limits";
  private static final String REASON_ELEMENTS = "elements";
  private static final String REASON_ERROR_TYPES = "errorTypes";
  private static final String REASON_RUNS = "runs";
  private static final String REASON_MESSAGE = "message";
  private static final String REASON_TOOL_NAME = "toolName";
  // Stands in for a missing limit, element or error type, so every reason has a bucket.
  private static final String NONE = "";
  private static final int MAX_KINDS = 3;
  private static final int MAX_LIMITS = 3;
  private static final int MAX_REASON_ELEMENTS = 100;
  private static final int MAX_ERROR_TYPES = 10;
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private AgentRunQueriesES() {}

  /** Runs of completed process instances, optionally of one process and one agent. */
  static BoolQuery completedRuns(
      final OffsetDateTime endedFrom, final String processDefinitionKey, final String agentId) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    query.filter(f -> f.exists(e -> e.field(AgentRunIndex.END_DATE)));
    if (endedFrom != null) {
      query.filter(
          f ->
              f.range(
                  r ->
                      r.date(
                          d ->
                              d.field(AgentRunIndex.END_DATE)
                                  .gte(FORMATTER.format(endedFrom))
                                  .format(OPTIMIZE_DATE_FORMAT))));
    }
    if (processDefinitionKey != null) {
      query.filter(
          f ->
              f.term(
                  t -> t.field(AgentRunIndex.PROCESS_DEFINITION_KEY).value(processDefinitionKey)));
    }
    if (agentId != null) {
      query.filter(f -> f.term(t -> t.field(AgentRunIndex.AGENT_ID).value(agentId)));
    }
    return query.build();
  }

  /** All runs of the bucket; a filter so its doc count reads like the others. */
  static Aggregation runs() {
    return Aggregation.of(a -> a.filter(f -> f.matchAll(m -> m)));
  }

  /** Incident runs: an incident on the agent or its tools, or a limit hit. */
  static Aggregation failedRuns() {
    return Aggregation.of(a -> a.filter(ProcessViewAgentRunRateInterpreterES.failedQuery()));
  }

  /** Runs per limit hit. */
  static Aggregation limits() {
    return Aggregation.of(a -> a.terms(t -> t.field(AgentRunIndex.LIMIT_HIT).size(MAX_LIMITS)));
  }

  /**
   * Runs per failure reason: by kind, then by limit, element and error type, with the first message
   * and the tool name.
   */
  static Aggregation failureReasons() {
    final Aggregation byErrorType =
        terms(AgentRunIndex.FAILURE_REASON_ERROR_TYPE, MAX_ERROR_TYPES)
            .aggregations(REASON_RUNS, r -> r.reverseNested(rn -> rn))
            .aggregations(
                REASON_MESSAGE,
                m ->
                    m.terms(
                        t -> t.field(reasonField(AgentRunIndex.FAILURE_REASON_MESSAGE)).size(1)))
            .aggregations(
                REASON_TOOL_NAME,
                m ->
                    m.terms(
                        t -> t.field(reasonField(AgentRunIndex.FAILURE_REASON_TOOL_NAME)).size(1)))
            .build();
    final Aggregation byElement =
        terms(AgentRunIndex.FAILURE_REASON_FLOW_NODE_ID, MAX_REASON_ELEMENTS)
            .aggregations(REASON_ERROR_TYPES, byErrorType)
            .build();
    final Aggregation byLimit =
        terms(AgentRunIndex.FAILURE_REASON_LIMIT, MAX_LIMITS)
            .aggregations(REASON_ELEMENTS, byElement)
            .build();
    final Aggregation byKind =
        terms(AgentRunIndex.FAILURE_REASON_KIND, MAX_KINDS)
            .aggregations(REASON_LIMITS, byLimit)
            .build();
    return Aggregation.of(
        a ->
            a.nested(n -> n.path(AgentRunIndex.FAILURE_REASONS))
                .aggregations(REASON_KINDS, byKind));
  }

  private static Aggregation.Builder.ContainerBuilder terms(final String field, final int size) {
    return new Aggregation.Builder()
        .terms(t -> t.field(reasonField(field)).missing(NONE).size(size));
  }

  private static String reasonField(final String field) {
    return AgentRunIndex.FAILURE_REASONS + "." + field;
  }

  /** The reasons of {@link #failureReasons()}, ranked by runs. */
  static List<FailureReason> failureReasons(final Map<String, Aggregate> aggs) {
    final List<FailureReason> reasons = new ArrayList<>();
    final Aggregate nested = aggs.get(FAILURE_REASONS);
    if (nested == null) {
      return reasons;
    }
    for (final StringTermsBucket kind :
        nested.nested().aggregations().get(REASON_KINDS).sterms().buckets().array()) {
      for (final StringTermsBucket limit :
          kind.aggregations().get(REASON_LIMITS).sterms().buckets().array()) {
        addReasons(kind.key().stringValue(), limit, reasons);
      }
    }
    reasons.sort(Comparator.comparingLong(FailureReason::runs).reversed());
    return reasons;
  }

  private static void addReasons(
      final String kind, final StringTermsBucket limit, final List<FailureReason> reasons) {
    for (final StringTermsBucket element :
        limit.aggregations().get(REASON_ELEMENTS).sterms().buckets().array()) {
      for (final StringTermsBucket errorType :
          element.aggregations().get(REASON_ERROR_TYPES).sterms().buckets().array()) {
        reasons.add(
            new FailureReason(
                kind,
                orNull(limit.key().stringValue()),
                orNull(element.key().stringValue()),
                firstKey(errorType.aggregations().get(REASON_TOOL_NAME)),
                orNull(errorType.key().stringValue()),
                firstKey(errorType.aggregations().get(REASON_MESSAGE)),
                errorType.aggregations().get(REASON_RUNS).reverseNested().docCount()));
      }
    }
  }

  private static String orNull(final String key) {
    return NONE.equals(key) ? null : key;
  }

  static String firstKey(final Aggregate terms) {
    return terms == null
        ? null
        : terms.sterms().buckets().array().stream()
            .map(bucket -> bucket.key().stringValue())
            .findFirst()
            .orElse(null);
  }

  static List<String> keys(final Aggregate terms) {
    return terms == null
        ? List.of()
        : terms.sterms().buckets().array().stream().map(b -> b.key().stringValue()).toList();
  }

  static Double rate(final long count, final long runs) {
    return runs == 0 ? null : (double) count / runs * 100;
  }

  /**
   * The value of one percentile, or null when no run in the bucket has the field, for example runs
   * imported before the field was added, for which Elasticsearch returns a null value.
   */
  static Double percentile(final Aggregate percentiles, final double percent) {
    final String value =
        percentiles.tdigestPercentiles().values().keyed().get(String.valueOf(percent));
    return value == null || value.isEmpty() ? null : finite(Double.parseDouble(value));
  }

  /**
   * The average of a stats aggregation, or null when no run in the bucket has the field. A plain
   * avg aggregation can't tell that apart from an average of 0.
   */
  static Double average(final Aggregate stats) {
    return stats.stats().count() == 0 ? null : finite(stats.stats().avg());
  }

  static Double finite(final double value) {
    return Double.isFinite(value) ? value : null;
  }
}
