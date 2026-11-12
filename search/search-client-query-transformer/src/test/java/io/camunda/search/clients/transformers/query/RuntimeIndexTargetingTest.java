/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class RuntimeIndexTargetingTest {

  private static final String ACTIVE = ProcessInstanceState.ACTIVE.name();
  private static final String SUSPENDED = ProcessInstanceState.SUSPENDED.name();
  private static final String COMPLETED = ProcessInstanceState.COMPLETED.name();

  private final IndexDescriptors indexDescriptors = new IndexDescriptors("", true);
  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(indexDescriptors);

  @ParameterizedTest(name = "{0}")
  @MethodSource("runtimeOnlyQueries")
  void shouldSearchOnlyRuntimeIndex(
      final String description,
      final TypedSearchQuery<?, ?> query,
      final Class<? extends IndexTemplateDescriptor> template) {
    // when
    final var indices = transform(query);

    // then
    assertThat(indices).containsExactly(indexDescriptors.get(template).getFullQualifiedName());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("runtimeAndArchiveQueries")
  void shouldSearchRuntimeAndArchivedIndices(
      final String description,
      final TypedSearchQuery<?, ?> query,
      final Class<? extends IndexTemplateDescriptor> template) {
    // when
    final var indices = transform(query);

    // then
    assertThat(indices).containsExactly(indexDescriptors.get(template).getAlias());
  }

  @ParameterizedTest
  @EnumSource(ProcessInstanceState.class)
  void shouldTargetIndicesForEveryProcessInstanceState(final ProcessInstanceState state) {
    // given
    final var query =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(f -> f.states(state.name())));

    // when
    final var indices = transform(query);

    // then - the switch has no default, so a new state does not compile until it is classified
    final var expected =
        switch (state) {
          case ACTIVE, SUSPENDED -> runtimeIndices(ListViewTemplate.class);
          case COMPLETED, CANCELED -> aliasIndices(ListViewTemplate.class);
        };
    assertThat(indices).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(UserTaskState.class)
  void shouldTargetIndicesForEveryUserTaskState(final UserTaskState state) {
    // given
    final var query =
        SearchQueryBuilders.userTaskSearchQuery(q -> q.filter(f -> f.states(state.name())));

    // when
    final var indices = transform(query);

    // then - the switch has no default, so a new state does not compile until it is classified
    final var expected =
        switch (state) {
          case CREATING, CREATED, ASSIGNING, UPDATING, COMPLETING, CANCELING ->
              runtimeIndices(TaskTemplate.class);
          // FAILED applies only to job-based user tasks; it reads all indices to stay safe
          case COMPLETED, CANCELED, FAILED -> aliasIndices(TaskTemplate.class);
        };
    assertThat(indices).isEqualTo(expected);
  }

  private List<String> runtimeIndices(final Class<? extends IndexTemplateDescriptor> template) {
    return List.of(indexDescriptors.get(template).getFullQualifiedName());
  }

  private List<String> aliasIndices(final Class<? extends IndexTemplateDescriptor> template) {
    return List.of(indexDescriptors.get(template).getAlias());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<String> transform(final TypedSearchQuery query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass()).apply(query).index();
  }

  static Stream<Arguments> runtimeOnlyQueries() {
    return Stream.of(
        processInstances("active process instances", f -> f.states(ACTIVE)),
        processInstances("suspended process instances", f -> f.states(SUSPENDED)),
        processInstances(
            "active or suspended process instances",
            f -> f.stateOperations(List.of(Operation.in(ACTIVE, SUSPENDED)))),
        processInstances("process instances with incident", f -> f.hasIncident(true)),
        processInstances(
            "Operate default filter: active or with incident",
            f ->
                f.orFilters(
                    List.of(
                        FilterBuilders.processInstance(
                            o -> o.stateOperations(List.of(Operation.in(ACTIVE)))),
                        FilterBuilders.processInstance(o -> o.hasIncident(true))))),
        processInstances(
            "active process instances with OR branches on other fields",
            f ->
                f.states(ACTIVE)
                    .orFilters(
                        List.of(
                            FilterBuilders.processInstance(o -> o.processDefinitionIds("a")),
                            FilterBuilders.processInstance(o -> o.processDefinitionIds("b"))))),
        Arguments.of(
            "element statistics of active process instances",
            new ProcessDefinitionFlowNodeStatisticsQuery(
                new ProcessDefinitionStatisticsFilter.Builder(1L).states(ACTIVE).build()),
            ListViewTemplate.class),
        Arguments.of(
            "element statistics, Operate default filter",
            new ProcessDefinitionFlowNodeStatisticsQuery(
                new ProcessDefinitionStatisticsFilter.Builder(1L)
                    .orFilters(
                        List.of(
                            new ProcessDefinitionStatisticsFilter.Builder(1L)
                                .states(ACTIVE)
                                .build(),
                            new ProcessDefinitionStatisticsFilter.Builder(1L)
                                .hasIncident(true)
                                .build()))
                    .build()),
            ListViewTemplate.class),
        Arguments.of(
            "active instance statistics per process definition version",
            ProcessDefinitionInstanceVersionStatisticsQuery.of(
                q -> q.filter(new ProcessDefinitionInstanceVersionStatisticsFilter("p", null))),
            ListViewTemplate.class),
        Arguments.of(
            "active element instances",
            SearchQueryBuilders.flownodeInstanceSearchQuery(
                q -> q.filter(f -> f.states(FlowNodeState.ACTIVE.name()))),
            FlowNodeInstanceTemplate.class),
        Arguments.of(
            "active incidents",
            SearchQueryBuilders.incidentSearchQuery(
                q -> q.filter(f -> f.states(IncidentState.ACTIVE.name()))),
            IncidentTemplate.class),
        Arguments.of(
            "incident statistics of active incidents per process definition",
            IncidentProcessInstanceStatisticsByDefinitionQuery.of(
                q -> q.filter(f -> f.state(IncidentState.ACTIVE.name()).errorHashCode(1))),
            IncidentTemplate.class),
        Arguments.of(
            "created user tasks",
            SearchQueryBuilders.userTaskSearchQuery(
                q -> q.filter(f -> f.states(UserTaskState.CREATED.name()))),
            TaskTemplate.class));
  }

  static Stream<Arguments> runtimeAndArchiveQueries() {
    return Stream.of(
        processInstances("unfiltered process instances", f -> f),
        processInstances("completed process instances", f -> f.states(COMPLETED)),
        processInstances(
            "active or completed process instances",
            f -> f.stateOperations(List.of(Operation.in(ACTIVE, COMPLETED)))),
        processInstances(
            "process instances that are not active",
            f -> f.stateOperations(List.of(Operation.neq(ACTIVE)))),
        processInstances("process instances without incident", f -> f.hasIncident(false)),
        processInstances(
            "active or completed process instances via OR branches",
            f ->
                f.orFilters(
                    List.of(
                        FilterBuilders.processInstance(o -> o.states(ACTIVE)),
                        FilterBuilders.processInstance(o -> o.states(COMPLETED))))),
        Arguments.of(
            "element statistics of all process instances",
            new ProcessDefinitionFlowNodeStatisticsQuery(
                new ProcessDefinitionStatisticsFilter.Builder(1L).build()),
            ListViewTemplate.class),
        Arguments.of(
            "completed element instances",
            SearchQueryBuilders.flownodeInstanceSearchQuery(
                q -> q.filter(f -> f.states(FlowNodeState.COMPLETED.name()))),
            FlowNodeInstanceTemplate.class),
        Arguments.of(
            "resolved incidents",
            SearchQueryBuilders.incidentSearchQuery(
                q -> q.filter(f -> f.states(IncidentState.RESOLVED.name()))),
            IncidentTemplate.class),
        Arguments.of(
            "pending incidents",
            SearchQueryBuilders.incidentSearchQuery(
                q -> q.filter(f -> f.states(IncidentState.PENDING.name()))),
            IncidentTemplate.class),
        Arguments.of(
            "incident statistics of resolved incidents per process definition",
            IncidentProcessInstanceStatisticsByDefinitionQuery.of(
                q -> q.filter(f -> f.state(IncidentState.RESOLVED.name()).errorHashCode(1))),
            IncidentTemplate.class),
        Arguments.of(
            "completed user tasks",
            SearchQueryBuilders.userTaskSearchQuery(
                q -> q.filter(f -> f.states(UserTaskState.COMPLETED.name()))),
            TaskTemplate.class));
  }

  private static Arguments processInstances(
      final String description,
      final java.util.function.UnaryOperator<ProcessInstanceFilter.Builder> filter) {
    return Arguments.of(
        description,
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(f -> filter.apply(f))),
        ListViewTemplate.class);
  }
}
