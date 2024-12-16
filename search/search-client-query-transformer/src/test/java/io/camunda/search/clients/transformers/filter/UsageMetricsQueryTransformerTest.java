/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public final class UsageMetricsQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByStartTimeAndEndTimeAndEvent() {
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var filter = FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            rangeQuery -> {
              assertThat(rangeQuery)
                  .extracting("field", "gte", "lte")
                  .containsExactly(
                      "eventTime", "2021-01-01T00:00:00.000+0000", "2023-02-01T00:00:00.000+0000");
            });
  }
}
