/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.BaseProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.ElementInstanceFilter;
import io.camunda.gateway.protocol.model.ElementInstanceFilterFields;
import io.camunda.gateway.protocol.model.GroupFilter;
import io.camunda.gateway.protocol.model.GroupFilterFields;
import io.camunda.gateway.protocol.model.MappingRuleFilter;
import io.camunda.gateway.protocol.model.MappingRuleFilterFields;
import io.camunda.gateway.protocol.model.ProcessDefinitionStatisticsFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceFilterFields;
import io.camunda.gateway.protocol.model.RoleFilter;
import io.camunda.gateway.protocol.model.RoleFilterFields;
import io.camunda.gateway.protocol.model.StringFilterProperty;
import io.camunda.gateway.protocol.model.UserFilter;
import io.camunda.gateway.protocol.model.UserFilterFields;
import io.camunda.gateway.protocol.model.UserTaskFilter;
import io.camunda.gateway.protocol.model.UserTaskFilterFields;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every filter that supports {@code $or} must reject a group without any filter criterion, because
 * dropping it would silently widen the result set (and the scope of batch operations).
 */
class SearchQueryFilterMapperOrGroupTest {

  private static final String EMPTY_GROUP_ERROR =
      "$or[%d] must contain at least one filter criterion";

  @ParameterizedTest
  @MethodSource("orFilters")
  <G> void shouldRejectEmptyGroup(final OrFilterCase<G> orFilter) {
    // when
    final var result = orFilter.map(List.of(orFilter.emptyGroup().get()));

    // then
    assertThat(result.getLeft()).containsExactly(EMPTY_GROUP_ERROR.formatted(0));
  }

  @ParameterizedTest
  @MethodSource("orFilters")
  <G> void shouldRejectEmptyGroupBeforeValidGroup(final OrFilterCase<G> orFilter) {
    // when
    final var result =
        orFilter.map(List.of(orFilter.emptyGroup().get(), orFilter.validGroup().get()));

    // then
    assertThat(result.getLeft()).containsExactly(EMPTY_GROUP_ERROR.formatted(0));
  }

  @ParameterizedTest
  @MethodSource("orFilters")
  <G> void shouldReportIndexOfEmptyGroupAfterValidGroup(final OrFilterCase<G> orFilter) {
    // when
    final var result =
        orFilter.map(List.of(orFilter.validGroup().get(), orFilter.emptyGroup().get()));

    // then
    assertThat(result.getLeft()).containsExactly(EMPTY_GROUP_ERROR.formatted(1));
  }

  @ParameterizedTest
  @MethodSource("orFilters")
  <G> void shouldRejectGroupWithoutOperators(final OrFilterCase<G> orFilter) {
    // when
    final var result = orFilter.map(List.of(orFilter.operatorlessGroup().get()));

    // then
    assertThat(result.getLeft()).containsExactly(EMPTY_GROUP_ERROR.formatted(0));
  }

  @ParameterizedTest
  @MethodSource("orFilters")
  <G> void shouldMapNonEmptyGroups(final OrFilterCase<G> orFilter) {
    // when
    final var result =
        orFilter.map(List.of(orFilter.validGroup().get(), orFilter.validGroup().get()));

    // then
    assertThat(result.isRight()).describedAs("errors: %s", result).isTrue();
    assertThat(result.get()).hasSize(2);
  }

  @Test
  void shouldRejectEmptyGroupForBatchOperationFilter() {
    // given
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .$or(List.of(ProcessInstanceFilterFields.Builder.create().build()))
            .build();

    // when
    final var result = SearchQueryFilterMapper.toRequiredProcessInstanceFilter(filter);

    // then
    assertThat(result.getLeft()).containsExactly(EMPTY_GROUP_ERROR.formatted(0));
  }

  static Stream<OrFilterCase<?>> orFilters() {
    return Stream.of(
        new OrFilterCase<BaseProcessInstanceFilterFields>(
            "process definition statistics",
            () -> BaseProcessInstanceFilterFields.Builder.create().build(),
            () -> BaseProcessInstanceFilterFields.Builder.create().elementId(noOperators()).build(),
            () -> BaseProcessInstanceFilterFields.Builder.create().elementId(eq("task")).build(),
            groups ->
                SearchQueryFilterMapper.toProcessDefinitionStatisticsFilter(
                        1L, ProcessDefinitionStatisticsFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.ProcessDefinitionStatisticsFilter::orFilters)),
        new OrFilterCase<ProcessInstanceFilterFields>(
            "process instance",
            () -> ProcessInstanceFilterFields.Builder.create().build(),
            () ->
                ProcessInstanceFilterFields.Builder.create()
                    .processDefinitionId(noOperators())
                    .build(),
            () ->
                ProcessInstanceFilterFields.Builder.create()
                    .processDefinitionId(eq("process"))
                    .build(),
            groups ->
                SearchQueryFilterMapper.toProcessInstanceFilter(
                        ProcessInstanceFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.ProcessInstanceFilter::orFilters)),
        new OrFilterCase<ElementInstanceFilterFields>(
            "element instance",
            () -> ElementInstanceFilterFields.Builder.create().build(),
            () -> ElementInstanceFilterFields.Builder.create().elementName(noOperators()).build(),
            () -> ElementInstanceFilterFields.Builder.create().elementName(eq("Task")).build(),
            groups ->
                SearchQueryFilterMapper.toElementInstanceFilter(
                        ElementInstanceFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.FlowNodeInstanceFilter::orFilters)),
        new OrFilterCase<UserTaskFilterFields>(
            "user task",
            () -> UserTaskFilterFields.Builder.create().build(),
            () -> UserTaskFilterFields.Builder.create().assignee(noOperators()).build(),
            () -> UserTaskFilterFields.Builder.create().assignee(eq("demo")).build(),
            groups ->
                SearchQueryFilterMapper.toUserTaskFilter(
                        UserTaskFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.UserTaskFilter::orFilters)),
        new OrFilterCase<GroupFilterFields>(
            "group",
            () -> GroupFilterFields.Builder.create().build(),
            () -> GroupFilterFields.Builder.create().groupId(noOperators()).build(),
            () -> GroupFilterFields.Builder.create().groupId(eq("group")).build(),
            groups ->
                SearchQueryFilterMapper.toGroupFilter(
                        GroupFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.GroupFilter::orFilters)),
        new OrFilterCase<RoleFilterFields>(
            "role",
            () -> RoleFilterFields.Builder.create().build(),
            () -> RoleFilterFields.Builder.create().roleId(noOperators()).build(),
            () -> RoleFilterFields.Builder.create().roleId(eq("role")).build(),
            groups ->
                SearchQueryFilterMapper.toRoleFilter(
                        RoleFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.RoleFilter::orFilters)),
        new OrFilterCase<MappingRuleFilterFields>(
            "mapping rule",
            () -> MappingRuleFilterFields.Builder.create().build(),
            () -> MappingRuleFilterFields.Builder.create().mappingRuleId(noOperators()).build(),
            () -> MappingRuleFilterFields.Builder.create().mappingRuleId(eq("rule")).build(),
            groups ->
                SearchQueryFilterMapper.toMappingRuleFilter(
                        MappingRuleFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.MappingRuleFilter::orFilters)),
        new OrFilterCase<UserFilterFields>(
            "user",
            () -> UserFilterFields.Builder.create().build(),
            () -> UserFilterFields.Builder.create().email(noOperators()).build(),
            () -> UserFilterFields.Builder.create().email(eq("demo@example.com")).build(),
            groups ->
                SearchQueryFilterMapper.toUserFilter(
                        UserFilter.Builder.create().$or(groups).build())
                    .map(io.camunda.search.filter.UserFilter::orFilters)));
  }

  /**
   * The request-model equivalent of {@code {"field": {}}}: an advanced filter without operators.
   */
  private static StringFilterProperty noOperators() {
    return AdvancedStringFilter.Builder.create().build();
  }

  private static StringFilterProperty eq(final String value) {
    return AdvancedStringFilter.Builder.create().$eq(value).build();
  }

  /**
   * @param emptyGroup a group without any field, i.e. {@code {}}
   * @param operatorlessGroup a group whose only field carries no operator, e.g. {@code {"field":
   *     {}}}
   * @param validGroup a group with one criterion
   * @param mapper maps a filter with the given {@code $or} groups to its mapped domain groups
   */
  record OrFilterCase<G>(
      String name,
      Supplier<G> emptyGroup,
      Supplier<G> operatorlessGroup,
      Supplier<G> validGroup,
      Function<List<G>, Either<List<String>, ? extends List<?>>> mapper) {

    Either<List<String>, ? extends List<?>> map(final List<G> groups) {
      return mapper.apply(groups);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
