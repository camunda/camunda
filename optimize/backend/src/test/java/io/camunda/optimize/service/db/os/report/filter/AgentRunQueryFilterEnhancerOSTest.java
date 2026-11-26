/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;

class AgentRunQueryFilterEnhancerOSTest {

  private static final FilterContext FILTER_CONTEXT =
      FilterContext.builder().timezone(ZoneId.of("Europe/Berlin")).build();

  private final AgentRunQueryFilterEnhancerOS underTest = new AgentRunQueryFilterEnhancerOS();

  @Test
  void shouldFilterOnTheAgentRunEndDate() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter()
            .fixedInstanceEndDate()
            .start(OffsetDateTime.parse("2026-10-14T00:00:00+02:00"))
            .end(OffsetDateTime.parse("2026-11-13T23:59:59+01:00"))
            .add()
            .buildList();

    // when
    final List<Query> queries = underTest.filterQueries(filters, FILTER_CONTEXT);

    // then
    assertThat(queries)
        .singleElement()
        .satisfies(
            query -> {
              assertThat(query.isRange()).isTrue();
              assertThat(toJson(query)).contains("\"" + AgentRunIndex.END_DATE + "\"");
            });
  }

  @Test
  void shouldCountOnlyRunsWithAnEndDateWhenOnlyCompletedRunsAreRequested() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();

    // when
    final List<Query> queries = underTest.filterQueries(filters, FILTER_CONTEXT);

    // then
    assertThat(queries)
        .singleElement()
        .satisfies(query -> assertThat(query.exists().field()).isEqualTo(AgentRunIndex.END_DATE));
  }

  @Test
  void shouldAddNoFilterWhenNoneIsRequested() {
    // when / then
    assertThat(underTest.filterQueries(List.of(), FILTER_CONTEXT)).isEmpty();
  }

  @Test
  void shouldRejectFiltersOnFieldsAgentRunsDoNotHave() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList();

    // when / then
    assertThatThrownBy(() -> underTest.filterQueries(filters, FILTER_CONTEXT))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("RunningInstancesOnlyFilterDto");
  }

  private static String toJson(final Query query) {
    final JsonpMapper mapper = new JacksonJsonpMapper();
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
      query.serialize(generator, mapper);
    }
    return writer.toString();
  }
}
