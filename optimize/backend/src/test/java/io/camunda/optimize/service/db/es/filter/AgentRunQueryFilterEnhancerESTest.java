/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunQueryFilterEnhancerESTest {

  private static final FilterContext FILTER_CONTEXT =
      FilterContext.builder().timezone(ZoneId.of("Europe/Berlin")).build();

  private final AgentRunQueryFilterEnhancerES underTest = new AgentRunQueryFilterEnhancerES();

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
    final List<Query> queries = filterQueries(filters);

    // then
    assertThat(queries)
        .singleElement()
        .satisfies(
            query -> {
              assertThat(query.isRange()).isTrue();
              assertThat(query.range().date().field()).isEqualTo(AgentRunIndex.END_DATE);
            });
  }

  @Test
  void shouldCountOnlyRunsWithAnEndDateWhenOnlyCompletedRunsAreRequested() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();

    // when
    final List<Query> queries = filterQueries(filters);

    // then
    assertThat(queries)
        .singleElement()
        .satisfies(query -> assertThat(query.exists().field()).isEqualTo(AgentRunIndex.END_DATE));
  }

  @Test
  void shouldAddNoFilterWhenNoneIsRequested() {
    // when / then
    assertThat(filterQueries(List.of())).isEmpty();
  }

  @Test
  void shouldRejectFiltersOnFieldsAgentRunsDoNotHave() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList();

    // when / then
    assertThatThrownBy(() -> filterQueries(filters))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("RunningInstancesOnlyFilterDto");
  }

  private List<Query> filterQueries(final List<ProcessFilterDto<?>> filters) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    underTest.addFilterToQuery(query, filters, FILTER_CONTEXT);
    return query.build().filter();
  }
}
