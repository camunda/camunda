/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.query.filter.ProcessInstanceFilter.Builder;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProcessInstanceFilterTest {

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = new Builder().build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).isEmpty();
    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_");

    assertThat(processInstanceFilter.active()).isFalse();
    assertThat(processInstanceFilter.canceled()).isFalse();
    assertThat(processInstanceFilter.completed()).isFalse();
    assertThat(processInstanceFilter.finished()).isFalse();
    assertThat(processInstanceFilter.running()).isFalse();
    assertThat(processInstanceFilter.retriesLeft()).isFalse();
  }

  @Test
  public void shouldSetFilterValues() {
    // given
    final var processInstanceFilterBuilder = new Builder();

    // when
    final var processInstanceFilter =
        processInstanceFilterBuilder
            .active()
            .canceled()
            .completed()
            .finished()
            .retriesLeft()
            .running()
            .processInstanceKeys(List.of(1L))
            .variable(new VariableValueFilter.Builder().build())
            .build();

    // then
    assertThat(processInstanceFilter.processInstanceKeys()).contains(1L);
    assertThat(processInstanceFilter.index()).contains("operate-list-view-8.3.0_alias");

    assertThat(processInstanceFilter.active()).isTrue();
    assertThat(processInstanceFilter.canceled()).isTrue();
    assertThat(processInstanceFilter.completed()).isTrue();
    assertThat(processInstanceFilter.finished()).isTrue();
    assertThat(processInstanceFilter.running()).isTrue();
    assertThat(processInstanceFilter.retriesLeft()).isTrue();
  }
}
