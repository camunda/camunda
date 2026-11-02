/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import java.util.HashMap;
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

  private static Aggregate percentiles(final Map<String, String> values) {
    return Aggregate.of(a -> a.tdigestPercentiles(t -> t.values(v -> v.keyed(values))));
  }
}
