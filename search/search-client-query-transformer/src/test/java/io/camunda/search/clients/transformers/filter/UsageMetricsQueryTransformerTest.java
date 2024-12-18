/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public final class UsageMetricsQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByStartTimeAndEndTimeAndEvent() {
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var filter =
        FilterBuilders.usageMetrics(
            f -> f.startTime(startTime).endTime(endTime).events("EVENT_PROCESS_INSTANCE_START"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            t -> {
              final var musts = t.must();
              assertThat(musts).hasSize(3);
              final var eventSearchTermQuery =
                  new SearchTermQuery.Builder()
                      .field("event")
                      .value("EVENT_PROCESS_INSTANCE_START")
                      .build()
                      .toSearchQuery();
              assertThat(musts).contains(eventSearchTermQuery);
              final var rangeQueries =
                  musts.stream()
                      .filter(
                          q ->
                              q.queryOption() instanceof SearchRangeQuery
                                  && ((SearchRangeQuery) q.queryOption())
                                      .field()
                                      .equals("eventTime"))
                      .map(q -> (SearchRangeQuery) q.queryOption())
                      .toList();
              assertThat(rangeQueries).hasSize(2);
              assertThat(rangeQueries)
                  .anySatisfy(q -> assertThat(q.gte()).isEqualTo("2021-01-01T00:00:00.000+0000"));
              assertThat(rangeQueries)
                  .anySatisfy(q -> assertThat(q.lte()).isEqualTo("2023-01-01T00:00:00.000+0000"));
            });
  }

  @Test
  public void shouldQueryDifferentIndicesDependingOnEvent() {
    // given
    final TasklistMetricIndex tasklistMetricIndex = mock(TasklistMetricIndex.class);
    final MetricIndex operateMetricIndex = mock(MetricIndex.class);
    final var transformer =
        new UsageMetricsFilterTransformer(tasklistMetricIndex, operateMetricIndex);
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    // should use Operate
    var filter =
        FilterBuilders.usageMetrics(
            f ->
                f.startTime(startTime)
                    .endTime(endTime)
                    .events("EVENT_PROCESS_INSTANCE_START", "EVENT_PROCESS_INSTANCE_END"));

    // when
    transformer.toSearchQuery(filter);
    assertThat(transformer.getIndex()).isEqualTo(operateMetricIndex);

    // should use Tasklist
    filter =
        FilterBuilders.usageMetrics(
            f -> f.startTime(startTime).endTime(endTime).events("task_completed_by_assignee"));
    // when
    transformer.toSearchQuery(filter);
    assertThat(transformer.getIndex()).isEqualTo(tasklistMetricIndex);
  }
}
