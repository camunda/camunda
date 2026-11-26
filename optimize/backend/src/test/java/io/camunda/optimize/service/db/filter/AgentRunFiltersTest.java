/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunFiltersTest {

  private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-14T00:00:00+02:00");
  private static final OffsetDateTime END = OffsetDateTime.parse("2026-11-13T23:59:59+01:00");

  @Test
  void shouldAcceptEndDateAndCompletedFilters() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter()
            .fixedInstanceEndDate()
            .start(START)
            .end(END)
            .add()
            .completedInstancesOnly()
            .add()
            .buildList();

    // when / then
    assertThatCode(() -> AgentRunFilters.validate(filters)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectFiltersOnFieldsAgentRunsDoNotHave() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter().fixedInstanceStartDate().start(START).add().buildList();

    // when / then
    assertThatThrownBy(() -> AgentRunFilters.validate(filters))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("InstanceStartDateFilterDto");
  }

  @Test
  void shouldReturnOnlyInstanceLevelEndDateFilters() {
    // given
    final List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder.filter()
            .fixedInstanceEndDate()
            .start(START)
            .end(END)
            .add()
            .fixedInstanceEndDate()
            .start(START)
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .completedInstancesOnly()
            .add()
            .buildList();

    // when
    final List<DateFilterDataDto<?>> endDateFilters = AgentRunFilters.endDateFilters(filters);

    // then
    assertThat(endDateFilters)
        .singleElement()
        .satisfies(
            data -> {
              assertThat(data.getStart()).isEqualTo(START);
              assertThat(data.getEnd()).isEqualTo(END);
            });
  }

  @Test
  void shouldTellWhetherOnlyCompletedRunsCount() {
    // given
    final List<ProcessFilterDto<?>> completedOnly =
        ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
    final List<ProcessFilterDto<?>> endDateOnly =
        ProcessFilterBuilder.filter().fixedInstanceEndDate().start(START).add().buildList();

    // when / then
    assertThat(AgentRunFilters.isCompletedOnly(completedOnly)).isTrue();
    assertThat(AgentRunFilters.isCompletedOnly(endDateOnly)).isFalse();
  }
}
