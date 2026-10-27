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
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AgentDefinitionFieldSortingTransformerTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments(
            AgentDefinitionIndex.KEY, SortOrder.DESC, s -> s.agentDefinitionKey().desc()),
        new TestArguments(AgentDefinitionIndex.AGENT_TYPE, SortOrder.ASC, s -> s.agentType().asc()),
        new TestArguments(AgentDefinitionIndex.NAME, SortOrder.ASC, s -> s.name().asc()),
        new TestArguments(
            AgentDefinitionIndex.ELEMENT_ID, SortOrder.DESC, s -> s.elementId().desc()),
        new TestArguments(
            AgentDefinitionIndex.BPMN_PROCESS_ID,
            SortOrder.ASC,
            s -> s.processDefinitionId().asc()),
        new TestArguments(
            AgentDefinitionIndex.PROCESS_DEFINITION_KEY,
            SortOrder.DESC,
            s -> s.processDefinitionKey().desc()),
        new TestArguments(
            AgentDefinitionIndex.PROCESS_DEFINITION_VERSION,
            SortOrder.ASC,
            s -> s.processDefinitionVersion().asc()),
        new TestArguments(
            AgentDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG,
            SortOrder.DESC,
            s -> s.processDefinitionVersionTag().desc()),
        new TestArguments(AgentDefinitionIndex.TENANT_ID, SortOrder.ASC, s -> s.tenantId().asc()));
  }

  @ParameterizedTest(name = "should sort by {0} in ''{1}'' direction")
  @MethodSource("provideSortParameters")
  void shouldSortByField(
      final String expectedField,
      final SortOrder sortOrder,
      final Function<AgentDefinitionSort.Builder, ObjectBuilder<AgentDefinitionSort>> fn) {
    final var request = SearchQueryBuilders.agentDefinitionSearchQuery(q -> q.sort(fn));
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
              assertThat(t.field().field()).isEqualTo(AgentDefinitionIndex.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldUseAgentDefinitionKeyAsDefaultTiebreakerWhenNoSortSpecified() {
    // given — query with no explicit sort
    final var request = SearchQueryBuilders.agentDefinitionSearchQuery(q -> q);
    // when
    final var sort = transformRequest(request);
    // then — only the implicit default tiebreaker is appended
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(AgentDefinitionIndex.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldThrowForUnknownSortField() {
    final var transformer = new AgentDefinitionFieldSortingTransformer();

    assertThatThrownBy(() -> transformer.apply("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownField");
  }

  private record TestArguments(
      String expectedField,
      SortOrder sortOrder,
      Function<AgentDefinitionSort.Builder, ObjectBuilder<AgentDefinitionSort>> fn)
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
