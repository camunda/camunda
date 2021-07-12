/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardAssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardCandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardBooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIdentityFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardLongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.AVAILABLE_FILTERS;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.FILTER_DATA;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class MigrateDashboardFilterIT extends AbstractUpgrade34IT {
  private static final DashboardDefinitionRestDto DASHBOARD_NULL_FILTERS = createDashboard(
    "dashboard-null-filters", "Dashboard with null filters", null
  );
  private static final DashboardDefinitionRestDto DASHBOARD_EMPTY_FILTERS = createDashboard(
    "dashboard-empty-filters", "Dashboard with empty filters", Collections.emptyList()
  );
  private static final DashboardDefinitionRestDto DASHBOARD_WITH_FILTERS_1 = createDashboard(
    "dashboard-with-filters-1", "Dashboard with filters 1", createFiltersForDashboard1()
  );
  private static final DashboardDefinitionRestDto DASHBOARD_WITH_FILTERS_2 = createDashboard(
    "dashboard-with-filters-2", "Dashboard with filters 2", createFiltersForDashboard2()
  );

  private static final Map<String, DashboardDefinitionRestDto> EXPECTED_DASHBOARDS_MAP = Map.of(
    "dashboard-null-filters", DASHBOARD_NULL_FILTERS,
    "dashboard-empty-filters", DASHBOARD_EMPTY_FILTERS,
    "dashboard-with-filters-1", DASHBOARD_WITH_FILTERS_1,
    "dashboard-with-filters-2", DASHBOARD_WITH_FILTERS_2
  );

  @SuppressWarnings(UNCHECKED_CAST)
  @SneakyThrows
  @Test
  public void dashboardFiltersAllHaveDataAndDefaultValues() {
    // given
    executeBulk("steps/3.4/dashboards/34-dashboards-with-filters.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);
    final SearchHit[] hitsAfterUpgrade = getAllDocumentsOfIndex(new DashboardIndex().getIndexName());
    final List<DashboardDefinitionRestDto> dashboardsAfterUpgrade =
      getAllDocumentsOfIndexAs(new DashboardIndex().getIndexName(), DashboardDefinitionRestDto.class);

    // then the mapping has been updated (all filters have data and all data has a defaultValues field)
    assertThat(hitsAfterUpgrade)
      .hasSize(4)
      .extracting(this::extractAvailableFilters)
      .filteredOn(Objects::nonNull)
      .allSatisfy(filters -> {
        for (Map<String, Object> filter : filters) {
          assertThat(filter).containsKey(FILTER_DATA);
          assertThat((Map<String, Object>) filter.get(FILTER_DATA)).containsKey("defaultValues");
        }
      });
    // and the content for each Dashboard is unchanged
    assertThat(dashboardsAfterUpgrade)
      .hasSize(4)
      .allSatisfy(dashboard -> {
        assertThat(dashboard)
          .usingRecursiveComparison()
          .isEqualTo(EXPECTED_DASHBOARDS_MAP.get(dashboard.getId()));
      });
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private List<Map<String, Object>> extractAvailableFilters(SearchHit dashboard) {
    return (List<Map<String, Object>>) dashboard.getSourceAsMap().get(AVAILABLE_FILTERS);
  }

  private static DashboardDefinitionRestDto createDashboard(final String id, final String name,
                                                            final List<DashboardFilterDto<?>> availableFilters) {
    DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto(
      Collections.singletonList(new ReportLocationDto(
        "58d57610-c4b8-42a2-928a-f30cb174d473", new PositionDto(10, 5),
        new DimensionDto(5, 10), null
      )));
    dashboard.setId(id);
    dashboard.setName(name);
    dashboard.setLastModifier("demo");
    dashboard.setOwner("demo");
    dashboard.setCreated(OffsetDateTime.parse("2021-06-10T10:00:00+02:00"));
    dashboard.setLastModified(OffsetDateTime.parse("2021-06-11T11:00:00+02:00"));
    dashboard.setCollectionId("collectionId");
    dashboard.setAvailableFilters(availableFilters);
    return dashboard;
  }

  private static List<DashboardFilterDto<?>> createFiltersForDashboard1() {
    return List.of(
      createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), true),
      createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1.0", "2.0"), true),
      createDashboardVariableFilter(VariableType.INTEGER, "integerVar", IN, Arrays.asList("1", "2"), true),
      createDashboardVariableFilter(VariableType.SHORT, "shortVar", IN, Arrays.asList("1", "2"), true),
      createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, true),
      createDashboardEndDateFilter(),
      createDashboardStateFilter()
    );
  }

  private static List<DashboardFilterDto<?>> createFiltersForDashboard2() {
    return List.of(
      createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar"),
      createDashboardVariableFilter(VariableType.DATE, "dateVar"),
      createDashboardVariableFilter(VariableType.STRING, "stringVar", IN, Arrays.asList("StringA", "StringB"), true),
      createDashboardVariableFilter(
        VariableType.STRING,
        "stringVar",
        NOT_CONTAINS,
        Collections.singletonList("StringC"),
        false
      ),
      createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), IN, true),
      createDashboardStartDateFilter(),
      createDashboardStateFilter()
    );
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName) {
    return createDashboardVariableFilter(type, variableName, null);
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName,
                                                                     final DashboardVariableFilterSubDataDto subData) {
    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    DashboardVariableFilterDataDto filterData;
    switch (type) {
      case DATE:
        filterData = new DashboardDateVariableFilterDataDto(variableName);
        break;
      case LONG:
        filterData = new DashboardLongVariableFilterDataDto(variableName, subData);
        break;
      case SHORT:
        filterData = new DashboardShortVariableFilterDataDto(variableName, subData);
        break;
      case DOUBLE:
        filterData = new DashboardDoubleVariableFilterDataDto(variableName, subData);
        break;
      case STRING:
        filterData = new DashboardStringVariableFilterDataDto(variableName, subData);
        break;
      case BOOLEAN:
        filterData = new DashboardBooleanVariableFilterDataDto(variableName);
        break;
      case INTEGER:
        filterData = new DashboardIntegerVariableFilterDataDto(variableName, subData);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
    variableFilter.setData(filterData);
    return variableFilter;
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName,
                                                                     final FilterOperator operator,
                                                                     final List<String> values,
                                                                     final boolean allowCustomValues) {
    switch (type) {
      case LONG:
      case SHORT:
      case DOUBLE:
      case STRING:
      case INTEGER:
        return createDashboardVariableFilter(
          type,
          variableName,
          new DashboardVariableFilterSubDataDto(operator, values, allowCustomValues)
        );
      case DATE:
      case BOOLEAN:
        return createDashboardVariableFilter(type, variableName, null);
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
  }

  private static DashboardFilterDto<?> createDashboardAssigneeFilter(final List<String> assigneeNames,
                                                                     final FilterOperator filterOperator,
                                                                     final boolean allowCustomValues) {
    final DashboardAssigneeFilterDto assigneeFilter = new DashboardAssigneeFilterDto();
    assigneeFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      assigneeNames,
      allowCustomValues
    ));
    return assigneeFilter;
  }


  private static DashboardFilterDto<?> createDashboardCandidateGroupFilter(final List<String> candidateGroupNames,
                                                                           final FilterOperator filterOperator,
                                                                           final boolean allowCustomValues) {
    final DashboardCandidateGroupFilterDto candidateGroupFilter = new DashboardCandidateGroupFilterDto();
    candidateGroupFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      candidateGroupNames,
      allowCustomValues
    ));
    return candidateGroupFilter;
  }

  private static DashboardFilterDto<?> createDashboardStateFilter() {
    final DashboardStateFilterDto stateFilter = new DashboardStateFilterDto();
    stateFilter.setData(new DashboardStateFilterDataDto(null));
    return stateFilter;
  }

  private static DashboardFilterDto<?> createDashboardStartDateFilter() {
    final DashboardStartDateFilterDto startDateFilter = new DashboardStartDateFilterDto();
    startDateFilter.setData(new DashboardDateFilterDataDto(null));
    return startDateFilter;
  }

  private static DashboardFilterDto<?> createDashboardEndDateFilter() {
    final DashboardStartDateFilterDto startDateFilter = new DashboardStartDateFilterDto();
    startDateFilter.setData(new DashboardDateFilterDataDto(null));
    return startDateFilter;
  }


}
