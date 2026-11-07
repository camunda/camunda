/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.FailureReason;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRunQueriesESTest {

  @Test
  void shouldReadThePercentileValue() {
    // given
    final Aggregate percentiles = percentiles(Map.of("50.0", "1200.0", "95.0", "4800.0"));

    // when / then
    assertThat(AgentRunQueriesES.percentile(percentiles, 50.0)).isEqualTo(1200.0);
    assertThat(AgentRunQueriesES.percentile(percentiles, 95.0)).isEqualTo(4800.0);
  }

  @Test
  void shouldReturnNullWhenNoRunHasTheField() {
    // given runs imported before the field existed: Elasticsearch returns a null percentile
    final Map<String, String> values = new HashMap<>();
    values.put("50.0", null);
    final Aggregate percentiles = percentiles(values);

    // when / then
    assertThat(AgentRunQueriesES.percentile(percentiles, 50.0)).isNull();
    assertThat(AgentRunQueriesES.percentile(percentiles, 95.0)).isNull();
  }

  @Test
  void shouldTellAnEmptyAverageFromAZeroAverage() {
    // given
    final Aggregate noValues =
        Aggregate.of(a -> a.stats(s -> s.count(0).min(0).max(0).avg(0).sum(0)));
    final Aggregate zero = Aggregate.of(a -> a.stats(s -> s.count(3).min(0).max(0).avg(0).sum(0)));

    // when / then
    assertThat(AgentRunQueriesES.average(noValues)).isNull();
    assertThat(AgentRunQueriesES.average(zero)).isZero();
  }

  @Test
  void shouldReadFailureReasonsByKindRankedByRuns() {
    // given one limit reason on 2 runs and one tool incident on 5 runs
    final Aggregate limit =
        terms(
            "LIMIT",
            Map.of(
                "limits",
                terms(
                    "TOKENS",
                    Map.of(
                        "elements",
                        terms("", Map.of("errorTypes", terms("", reason(2, null, null))))))));
    final Aggregate tool =
        terms(
            "TOOL_INCIDENT",
            Map.of(
                "limits",
                terms(
                    "",
                    Map.of(
                        "elements",
                        terms(
                            "lookup",
                            Map.of(
                                "errorTypes",
                                terms(
                                    "JOB_NO_RETRIES",
                                    reason(5, "HTTP 503", "Look up merchant"))))))));
    final Map<String, Aggregate> aggs =
        Map.of(
            "failureReasons",
            Aggregate.of(
                a ->
                    a.nested(
                        n -> n.docCount(7).aggregations(Map.of("kinds", merge(limit, tool))))));

    // when
    final List<FailureReason> reasons = AgentRunQueriesES.failureReasons(aggs);

    // then
    assertThat(reasons)
        .containsExactly(
            new FailureReason(
                "TOOL_INCIDENT",
                null,
                "lookup",
                "Look up merchant",
                "JOB_NO_RETRIES",
                "HTTP 503",
                5),
            new FailureReason("LIMIT", "TOKENS", null, null, null, null, 2));
  }

  private static Map<String, Aggregate> reason(
      final long runs, final String message, final String toolName) {
    final Map<String, Aggregate> sub = new HashMap<>();
    sub.put("runs", Aggregate.of(a -> a.reverseNested(r -> r.docCount(runs))));
    sub.put("message", message == null ? emptyTerms() : terms(message, Map.of()));
    sub.put("toolName", toolName == null ? emptyTerms() : terms(toolName, Map.of()));
    return sub;
  }

  private static Aggregate terms(final String key, final Map<String, Aggregate> sub) {
    return Aggregate.of(
        a ->
            a.sterms(
                t ->
                    t.sumOtherDocCount(0L)
                        .buckets(
                            b ->
                                b.array(
                                    List.of(
                                        StringTermsBucket.of(
                                            k ->
                                                k.key(FieldValue.of(key))
                                                    .docCount(1)
                                                    .aggregations(sub)))))));
  }

  private static Aggregate emptyTerms() {
    return Aggregate.of(
        a -> a.sterms(t -> t.sumOtherDocCount(0L).buckets(b -> b.array(List.of()))));
  }

  private static Aggregate merge(final Aggregate first, final Aggregate second) {
    final List<StringTermsBucket> buckets = new java.util.ArrayList<>();
    buckets.addAll(first.sterms().buckets().array());
    buckets.addAll(second.sterms().buckets().array());
    return Aggregate.of(a -> a.sterms(t -> t.sumOtherDocCount(0L).buckets(b -> b.array(buckets))));
  }

  private static Aggregate percentiles(final Map<String, String> values) {
    return Aggregate.of(a -> a.tdigestPercentiles(t -> t.values(v -> v.keyed(values))));
  }
}
