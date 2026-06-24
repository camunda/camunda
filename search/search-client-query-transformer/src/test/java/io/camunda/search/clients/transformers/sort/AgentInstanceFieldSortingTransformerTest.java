/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AgentInstanceFieldSortingTransformerTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments(
            AgentInstanceTemplate.KEY, SortOrder.DESC, s -> s.agentInstanceKey().desc()),
        new TestArguments(AgentInstanceTemplate.STATUS, SortOrder.ASC, s -> s.status().asc()),
        new TestArguments(
            AgentInstanceTemplate.ELEMENT_ID, SortOrder.ASC, s -> s.elementId().asc()),
        new TestArguments(
            ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
            SortOrder.ASC,
            s -> s.processInstanceKey().asc()),
        new TestArguments(
            ProcessInstanceDependant.ROOT_PROCESS_INSTANCE_KEY,
            SortOrder.DESC,
            s -> s.rootProcessInstanceKey().desc()),
        new TestArguments(
            AgentInstanceTemplate.PROCESS_DEFINITION_KEY,
            SortOrder.ASC,
            s -> s.processDefinitionKey().asc()),
        new TestArguments(
            AgentInstanceTemplate.TENANT_ID, SortOrder.DESC, s -> s.tenantId().desc()),
        new TestArguments(
            AgentInstanceTemplate.CREATION_DATE, SortOrder.ASC, s -> s.creationDate().asc()),
        new TestArguments(
            AgentInstanceTemplate.LAST_UPDATED_DATE,
            SortOrder.DESC,
            s -> s.lastUpdatedDate().desc()),
        new TestArguments(
            AgentInstanceTemplate.COMPLETION_DATE, SortOrder.ASC, s -> s.completionDate().asc()));
  }

  @ParameterizedTest(name = "should sort by {0} in ''{1}'' direction")
  @MethodSource("provideSortParameters")
  void shouldSortByField(
      final String expectedField,
      final SortOrder sortOrder,
      final Function<AgentInstanceSort.Builder, ObjectBuilder<AgentInstanceSort>> fn) {
    final var request = SearchQueryBuilders.agentInstanceSearchQuery(q -> q.sort(fn));
    final var sort = transformRequest(request);

    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(expectedField);
              assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(AgentInstanceTemplate.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldUseAgentInstanceKeyAsDefaultTiebreakerWhenNoSortSpecified() {
    // given — query with no explicit sort
    final var request = SearchQueryBuilders.agentInstanceSearchQuery(q -> q);
    // when
    final var sort = transformRequest(request);
    // then — only the implicit default tiebreaker is appended
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(AgentInstanceTemplate.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldThrowForUnknownSortField() {
    final var transformer = new AgentInstanceFieldSortingTransformer();

    assertThatThrownBy(() -> transformer.apply("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownField");
  }

  private record TestArguments(
      String expectedField,
      SortOrder sortOrder,
      Function<AgentInstanceSort.Builder, ObjectBuilder<AgentInstanceSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {expectedField, sortOrder, fn};
    }

    @Override
    public String toString() {
      return expectedField + " " + sortOrder;
    }
  }
}
