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

import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import org.junit.jupiter.api.Test;

public class ClusterVariableFilterTest {

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var filter = new ClusterVariableFilter.Builder().names("foo").build();

    // then
    assertThat(filter.nameOperations()).hasSize(1);
    assertThat(filter.valueOperations()).isEmpty();
  }

  @Test
  public void shouldSetFilters() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter =
        filterBuilder
            .names("name")
            .valueOperations(eq("equals"), gt("4"), gte("2"), lt("1"), lte("3"))
            .build();

    // then
    assertThat(filter.nameOperations()).hasSize(1);
    assertThat(filter.valueOperations()).hasSize(5);
  }

  @Test
  public void shouldSetValueOperationsWithUntypedOperation() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter =
        filterBuilder
            .names("name")
            .valueOperation(UntypedOperation.of(eq("equals")))
            .valueOperation(UntypedOperation.of(gt(4)))
            .valueOperation(UntypedOperation.of(gte(2)))
            .valueOperation(UntypedOperation.of(lt(1)))
            .valueOperation(UntypedOperation.of(lte(3)))
            .build();

    // then
    assertThat(filter.nameOperations()).hasSize(1);
    assertThat(filter.valueOperations()).hasSize(5);
  }

  @Test
  public void shouldSetScopeAndTenantIdFilters() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter =
        filterBuilder
            .names("name")
            .scopes("TENANT")
            .tenantIds("resource-123")
            .values("some-value")
            .build();

    // then
    assertThat(filter.nameOperations()).hasSize(1);
    assertThat(filter.scopeOperations()).hasSize(1);
    assertThat(filter.tenantIdOperations()).hasSize(1);
    assertThat(filter.valueOperations()).hasSize(1);
  }

  @Test
  public void shouldSetMultipleNames() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter = filterBuilder.names("name1", "name2", "name3").build();

    // then
    assertThat(filter.nameOperations()).hasSize(1);
    assertThat(filter.nameOperations().getFirst().operator()).isEqualTo(Operator.IN);
    assertThat(filter.nameOperations().getFirst().values()).hasSize(3);
  }

  @Test
  public void shouldSetMultipleTenantIds() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter = filterBuilder.tenantIds("resource1", "resource2", "resource3").build();

    // then
    assertThat(filter.tenantIdOperations()).hasSize(1);
    assertThat(filter.tenantIdOperations().getFirst().operator()).isEqualTo(Operator.IN);
    assertThat(filter.tenantIdOperations().getFirst().values()).hasSize(3);
  }

  @Test
  public void shouldSetMultipleValues() {
    // given
    final var filterBuilder = new ClusterVariableFilter.Builder();

    // when
    final var filter = filterBuilder.values("value1", "value2", "value3").build();

    // then
    assertThat(filter.valueOperations()).hasSize(1);
    assertThat(filter.valueOperations().getFirst().operator()).isEqualTo(Operator.IN);
    assertThat(filter.valueOperations().getFirst().values()).hasSize(3);
  }
}
