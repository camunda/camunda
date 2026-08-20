/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.filter.Operation.eq;
import static io.camunda.search.filter.Operation.gt;
import static io.camunda.search.filter.Operation.gte;
import static io.camunda.search.filter.Operation.lt;
import static io.camunda.search.filter.Operation.lte;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableValueFilter.Builder;
import org.junit.jupiter.api.Test;

public class VariableValueFilterTest {

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var filter = new Builder().name("foo").build();

    // then
    assertThat(filter.name()).isEqualTo("foo");
    assertThat(filter.valueOperations()).isEmpty();
  }

  @Test
  public void shouldSetFilters() {
    // given
    final var filterBuilder = new Builder();

    // when
    final var filter =
        filterBuilder
            .name("name")
            .valueOperation(UntypedOperation.of(eq("equals")))
            .valueOperation(UntypedOperation.of(gt(4)))
            .valueOperation(UntypedOperation.of(gte(2)))
            .valueOperation(UntypedOperation.of(lt(1)))
            .valueOperation(UntypedOperation.of(lte(3)))
            .build();

    // then
    assertThat(filter.name()).isEqualTo("name");
    assertThat(filter.valueOperations()).hasSize(5);
  }
}
