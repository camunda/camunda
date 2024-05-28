/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.service.query.filter.VariableValueFilter.Builder;
import org.junit.jupiter.api.Test;

public class VariableValueFilterTest {

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var filter = new Builder().build();

    // then
    assertThat(filter.eq()).isNull();
    assertThat(filter.gt()).isNull();
    assertThat(filter.gte()).isNull();
    assertThat(filter.lt()).isNull();
    assertThat(filter.lte()).isNull();
    assertThat(filter.name()).isNull();
  }

  @Test
  public void shouldSetFilters() {
    // given
    final var filterBuilder = new Builder();

    // when
    final var filter =
        filterBuilder
            .eq("equals")
            .gt("greaterThen")
            .gte("greaterThenOrEqual")
            .lt("lessThen")
            .lte("lessThenOrEqual")
            .name("name")
            .build();

    // then
    assertThat(filter.eq()).isEqualTo("equals");
    assertThat(filter.gt()).isEqualTo("greaterThen");
    assertThat(filter.gte()).isEqualTo("greaterThenOrEqual");
    assertThat(filter.lt()).isEqualTo("lessThen");
    assertThat(filter.lte()).isEqualTo("lessThenOrEqual");
    assertThat(filter.name()).isEqualTo("name");
  }

  @Test
  public void shouldConvertToDataStoreFilter() {
    // given
    final var filter =
        new VariableValueFilter.Builder()
            .eq("equals")
            .gt("greaterThen")
            .gte("greaterThenOrEqual")
            .lt("lessThen")
            .lte("lessThenOrEqual")
            .name("name")
            .build();

    // when
    final DataStoreQuery searchQuery = filter.toSearchQuery(); // fails

    // then
  }
}
