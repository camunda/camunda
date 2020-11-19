/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DashboardVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.upgrade.plan.UpgradeFrom32To33Factory;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class DashboardAvailableFiltersMigrationIT extends AbstractUpgrade32IT {

  @SneakyThrows
  @Test
  @SuppressWarnings("unchecked")
  public void dashboardFiltersAllowCustomVariableSetFalse() {
    // given
    executeBulk("steps/3.2/dashboards/32-dashboard-bulk");
    final UpgradePlan upgradePlan = UpgradeFrom32To33Factory.createUpgradePlan();

    // then
    final SearchHit[] dashboardsBeforeUpgrade = getAllDocumentsOfIndex(DASHBOARD_INDEX.getIndexName());
    assertThat(dashboardsBeforeUpgrade).hasSize(2);
    final List<Map<String, Object>> filtersBeforeUpgrade =
      (List<Map<String, Object>>) dashboardsBeforeUpgrade[0].getSourceAsMap().get(DashboardIndex.AVAILABLE_FILTERS);
    assertThat(filtersBeforeUpgrade).hasSize(10);
    // also assert that the other dashboard has no filters
    assertThat((List<Map<String, Object>>) dashboardsBeforeUpgrade[1].getSourceAsMap()
      .get(DashboardIndex.AVAILABLE_FILTERS))
      .isEmpty();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] dashboardsAfterUpgrade = getAllDocumentsOfIndex(DASHBOARD_INDEX.getIndexName());
    assertThat(dashboardsAfterUpgrade).hasSameSizeAs(dashboardsBeforeUpgrade);
    final List<Map<String, Object>> filtersAfterUpgrade =
      (List<Map<String, Object>>) dashboardsAfterUpgrade[0].getSourceAsMap().get(DashboardIndex.AVAILABLE_FILTERS);
    // the dashboard without filters is unaffected
    assertThat((List<Map<String, Object>>) dashboardsAfterUpgrade[1].getSourceAsMap()
      .get(DashboardIndex.AVAILABLE_FILTERS))
      .isEmpty();
    // no filters have been removed
    assertThat(filtersAfterUpgrade).hasSameSizeAs(filtersBeforeUpgrade);
    // non-variable filters are not affected
    assertThat(getNonVariableFilters(filtersAfterUpgrade))
      .hasSize(3)
      .allSatisfy(nonVariableFilter -> assertThat(nonVariableFilter.get(DashboardIndex.FILTER_DATA)).isNull());
    // the boolean and date variable filters no longer have data set
    assertThat(getBooleanAndDateVariableFilters(filtersAfterUpgrade))
      .hasSize(2)
      .allSatisfy(boolAndDateVariableFilters -> {
        final Map<String, Object> filterData =
          (Map<String, Object>) boolAndDateVariableFilters.get(DashboardIndex.FILTER_DATA);
        assertThat(filterData.get(DashboardVariableFilterDataDto.Fields.data)).isNull();
      });
    // String and number variable filters have `allowCustomVariables` set to `false`
    assertThat(getStringAndNumberVariableFilters(filtersAfterUpgrade))
      .hasSize(5)
      .allSatisfy(stringOrNumberVariableFilters -> {
        final Map<String, Object> filterData = (Map<String, Object>) stringOrNumberVariableFilters.get(DashboardIndex.FILTER_DATA);
        final Map<String, Object> filterSubData = (Map<String, Object>) filterData.get(DashboardIndex.FILTER_DATA);
        final Boolean allowCustomValues =
          (Boolean) filterSubData.get(DashboardVariableFilterSubDataDto.Fields.allowCustomValues);
        assertThat(allowCustomValues).isFalse();
      });
  }

  private List<Map<String, Object>> getNonVariableFilters(final List<Map<String, Object>> filters) {
    return filters.stream()
      .filter(filter -> {
        final String filterType = (String) filter.get(DashboardIndex.FILTER_TYPE);
        return DashboardFilterType.STATE.getId().equalsIgnoreCase(filterType)
          || DashboardFilterType.START_DATE.getId().equalsIgnoreCase(filterType)
          || DashboardFilterType.END_DATE.getId().equalsIgnoreCase(filterType);
      }).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getBooleanAndDateVariableFilters(final List<Map<String, Object>> filters) {
    return filters.stream()
      .filter(filter -> DashboardFilterType.VARIABLE.getId()
        .equalsIgnoreCase((String) filter.get(DashboardIndex.FILTER_TYPE)))
      .filter(varFilter -> {
        final Map<String, Object> filterData = (Map<String, Object>) varFilter.get(DashboardIndex.FILTER_DATA);
        final String variableType = (String) filterData.get(DashboardVariableFilterDataDto.Fields.type);
        return VariableType.DATE.getId().equalsIgnoreCase(variableType)
          || VariableType.BOOLEAN.getId().equalsIgnoreCase(variableType);
      }).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getStringAndNumberVariableFilters(final List<Map<String, Object>> filters) {
    return filters.stream()
      .filter(filter -> DashboardFilterType.VARIABLE.getId()
        .equalsIgnoreCase((String) filter.get(DashboardIndex.FILTER_TYPE)))
      .filter(varFilter -> {
        final Map<String, Object> filterData = (Map<String, Object>) varFilter.get(DashboardIndex.FILTER_DATA);
        final String variableType = (String) filterData.get(DashboardVariableFilterDataDto.Fields.type);
        return !VariableType.DATE.getId().equalsIgnoreCase(variableType)
          && !VariableType.BOOLEAN.getId().equalsIgnoreCase(variableType);
      }).collect(Collectors.toList());
  }

}
