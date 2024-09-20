/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.clients.sort.SortOrder;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.query.filter.ProcessDefinitionSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.ProcessDefinitionSort;
import io.camunda.service.search.sort.ProcessDefinitionSort.Builder;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProcessDefinitionSortTest {

  private ProcessDefinitionServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessDefinitionSearchQueryStub().registerWith(client);
    services = new ProcessDefinitionServices(null, client, null, null);
  }

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("key", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new TestArguments("name", SortOrder.ASC, s -> s.processName().asc()),
        new TestArguments("version", SortOrder.ASC, s -> s.processVersion().asc()),
        new TestArguments("versionTag", SortOrder.ASC, s -> s.processVersionTag().asc()),
        new TestArguments("bpmnProcessId", SortOrder.ASC, s -> s.bpmnProcessId().asc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new TestArguments("formKey", SortOrder.ASC, s -> s.formKey().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<Builder, ObjectBuilder<ProcessDefinitionSort>> fn) {
    // when
    services.search(SearchQueryBuilders.processDefinitionSearchQuery(q -> q.sort(fn)));

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var sort = searchRequest.sort();
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(field);
              assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo("key");
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  private record TestArguments(
      String field,
      SortOrder sortOrder,
      Function<ProcessDefinitionSort.Builder, ObjectBuilder<ProcessDefinitionSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
