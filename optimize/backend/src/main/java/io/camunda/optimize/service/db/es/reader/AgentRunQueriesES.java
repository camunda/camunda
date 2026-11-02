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
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
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

  static final String FAILED = "failed";
  static final String SUCCEEDED = "succeeded";
  static final String LIMITS = "limits";
  static final String INCIDENT_REASONS = "incidentReasons";
  private static final String REASON_ELEMENTS = "elements";
  private static final String REASON_ERROR_TYPES = "errorTypes";
  private static final String REASON_RUNS = "runs";
  private static final String REASON_MESSAGE = "message";
  private static final String REASON_TOOL_NAME = "toolName";
  private static final String UNKNOWN_ERROR_TYPE = "unknown";
  private static final int MAX_REASON_ELEMENTS = 100;
  private static final int MAX_ERROR_TYPES = 10;
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private AgentRunQueriesES() {}

  /** Runs of completed process instances, optionally of one process and one agent. */
  static BoolQuery completedRuns(
      final OffsetDateTime endedFrom, final String processDefinitionKey, final String agentId) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    query.filter(f -> f.exists(e -> e.field(ProcessInstanceIndex.END_DATE)));
    if (endedFrom != null) {
      query.filter(
          f ->
              f.range(
                  r ->
                      r.date(
                          d ->
                              d.field(ProcessInstanceIndex.END_DATE)
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

  static Aggregation succeededRuns() {
    return Aggregation.of(a -> a.filter(ProcessViewAgentRunRateInterpreterES.succeededQuery()));
  }

  static Aggregation failedRuns() {
    return Aggregation.of(
        a ->
            a.filter(
                f ->
                    f.bool(b -> b.mustNot(ProcessViewAgentRunRateInterpreterES.succeededQuery()))));
  }

  /** Runs per limit hit. */
  static Aggregation limits() {
    return Aggregation.of(a -> a.terms(t -> t.field(AgentRunIndex.LIMIT_HIT).size(10)));
  }

  /** Runs per element and error type of their incidents, with the first message and tool name. */
  static Aggregation incidentReasons() {
    return Aggregation.of(
        a ->
            a.nested(n -> n.path(AgentRunIndex.FAILURE_REASONS))
                .aggregations(
                    REASON_ELEMENTS,
                    e ->
                        e.terms(
                                t ->
                                    t.field(
                                            AgentRunIndex.FAILURE_REASONS
                                                + "."
                                                + AgentRunIndex.FAILURE_REASON_FLOW_NODE_ID)
                                        .size(MAX_REASON_ELEMENTS))
                            .aggregations(
                                REASON_ERROR_TYPES,
                                et ->
                                    et.terms(
                                            t ->
                                                t.field(
                                                        AgentRunIndex.FAILURE_REASONS
                                                            + "."
                                                            + AgentRunIndex
                                                                .FAILURE_REASON_ERROR_TYPE)
                                                    .missing(UNKNOWN_ERROR_TYPE)
                                                    .size(MAX_ERROR_TYPES))
                                        .aggregations(REASON_RUNS, r -> r.reverseNested(rn -> rn))
                                        .aggregations(
                                            REASON_MESSAGE,
                                            m ->
                                                m.terms(
                                                    t ->
                                                        t.field(
                                                                AgentRunIndex.FAILURE_REASONS
                                                                    + "."
                                                                    + AgentRunIndex
                                                                        .FAILURE_REASON_MESSAGE)
                                                            .size(1)))
                                        .aggregations(
                                            REASON_TOOL_NAME,
                                            m ->
                                                m.terms(
                                                    t ->
                                                        t.field(
                                                                AgentRunIndex.FAILURE_REASONS
                                                                    + "."
                                                                    + AgentRunIndex
                                                                        .FAILURE_REASON_TOOL_NAME)
                                                            .size(1))))));
  }

  /** Limit and incident reasons from {@link #limits()} and {@link #incidentReasons()}, by runs. */
  static List<FailureReason> failureReasons(final Map<String, Aggregate> aggs) {
    final List<FailureReason> reasons = new ArrayList<>();
    if (aggs.get(LIMITS) != null) {
      aggs.get(LIMITS)
          .sterms()
          .buckets()
          .array()
          .forEach(
              bucket ->
                  reasons.add(
                      new FailureReason(
                          FailureReason.KIND_LIMIT,
                          bucket.key().stringValue(),
                          null,
                          null,
                          null,
                          null,
                          bucket.docCount())));
    }
    if (aggs.get(INCIDENT_REASONS) != null) {
      for (final StringTermsBucket element :
          aggs.get(INCIDENT_REASONS)
              .nested()
              .aggregations()
              .get(REASON_ELEMENTS)
              .sterms()
              .buckets()
              .array()) {
        for (final StringTermsBucket errorType :
            element.aggregations().get(REASON_ERROR_TYPES).sterms().buckets().array()) {
          final String type = errorType.key().stringValue();
          reasons.add(
              new FailureReason(
                  FailureReason.KIND_INCIDENT,
                  null,
                  element.key().stringValue(),
                  firstKey(errorType.aggregations().get(REASON_TOOL_NAME)),
                  UNKNOWN_ERROR_TYPE.equals(type) ? null : type,
                  firstKey(errorType.aggregations().get(REASON_MESSAGE)),
                  errorType.aggregations().get(REASON_RUNS).reverseNested().docCount()));
        }
      }
    }
    reasons.sort(Comparator.comparingLong(FailureReason::runs).reversed());
    return reasons;
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
