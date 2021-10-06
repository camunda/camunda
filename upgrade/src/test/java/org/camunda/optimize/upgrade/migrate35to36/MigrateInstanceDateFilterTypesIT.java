/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade35To36PlanFactory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateInstanceDateFilterTypesIT extends AbstractUpgrade35IT {

  private final List<ProcessFilterDto<?>> EXPECTED_FILTERS_REPORT_1 = ProcessFilterBuilder.filter()
    .fixedInstanceStartDate()
    .start(OffsetDateTime.parse("2021-06-07T00:00:00+02:00"))
    .end(OffsetDateTime.parse("2021-06-08T00:00:00+02:00"))
    .add()
    .completedInstancesOnly()
    .add()
    .buildList();

  private final List<ProcessFilterDto<?>> EXPECTED_FILTERS_REPORT_2 = ProcessFilterBuilder.filter()
    .relativeInstanceStartDate()
    .start(1L, DateFilterUnit.YEARS)
    .add()
    .fixedInstanceEndDate()
    .start(OffsetDateTime.parse("2021-07-08T00:00:00+02:00"))
    .end(OffsetDateTime.parse("2021-07-10T00:00:00+02:00"))
    .add()
    .relativeFlowNodeStartDate()
    .filterLevel(FilterApplicationLevel.VIEW)
    .start(1L, DateFilterUnit.WEEKS)
    .add()
    .buildList();

  private final List<ProcessFilterDto<?>> EXPECTED_FILTERS_REPORT_3 = Collections.emptyList();

  private final List<DashboardFilterDto<?>> EXPECTED_FILTERS_DASHBOARD_1 = Arrays.asList(
    createDashboardInstanceStartDateFilter(),
    createDashboardInstanceEndDateFilter(),
    createDashboardStateFilter()
  );

  private final List<DashboardFilterDto<?>> EXPECTED_FILTERS_DASHBOARD_2 = Collections.emptyList();

  @SneakyThrows
  @Test
  public void reportInstanceDateFiltersAreMigrated() {
    // given
    executeBulk("steps/3.5/report/35-process-reports-with-date-filters.json");
    final UpgradePlan upgradePlan = new Upgrade35To36PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndexAs(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), ReportDefinitionDto.class))
      .hasSize(4);
    assertThat(getDocumentOfIndexByIdAs(
      SINGLE_PROCESS_REPORT_INDEX.getIndexName(),
      "report-1",
      ReportDefinitionDto.class
    )).isPresent()
      .map(report -> (ProcessReportDataDto) report.getData())
      .map(ProcessReportDataDto::getFilter)
      .contains(EXPECTED_FILTERS_REPORT_1);
    assertThat(getDocumentOfIndexByIdAs(
      SINGLE_PROCESS_REPORT_INDEX.getIndexName(),
      "report-2",
      ReportDefinitionDto.class
    )).isPresent()
      .map(report -> (ProcessReportDataDto) report.getData())
      .map(ProcessReportDataDto::getFilter)
      .contains(EXPECTED_FILTERS_REPORT_2);
    assertThat(getDocumentOfIndexByIdAs(
      SINGLE_PROCESS_REPORT_INDEX.getIndexName(),
      "report-3", // report with empty filter list
      ReportDefinitionDto.class
    )).isPresent()
      .map(report -> (ProcessReportDataDto) report.getData())
      .map(ProcessReportDataDto::getFilter)
      .contains(EXPECTED_FILTERS_REPORT_3);
    assertThat(getDocumentOfIndexByIdAs(
      SINGLE_PROCESS_REPORT_INDEX.getIndexName(),
      "report-4", // report with null filter list
      ReportDefinitionDto.class
    )).isPresent()
      .map(report -> (ProcessReportDataDto) report.getData())
      .map(ProcessReportDataDto::getFilter)
      .isEmpty();
  }

  @SneakyThrows
  @Test
  public void dashboardInstanceDateFiltersAreMigrated() {
    // given
    executeBulk("steps/3.5/dashboard/35-dashboards-with-date-filters.json");
    final UpgradePlan upgradePlan = new Upgrade35To36PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndexAs(DASHBOARD_INDEX.getIndexName(), DashboardDefinitionRestDto.class))
      .hasSize(3);
    assertThat(getDocumentOfIndexByIdAs(
      DASHBOARD_INDEX.getIndexName(),
      "dashboard-1",
      DashboardDefinitionRestDto.class
    )).isPresent()
      .map(BaseDashboardDefinitionDto::getAvailableFilters)
      .contains(EXPECTED_FILTERS_DASHBOARD_1);
    assertThat(getDocumentOfIndexByIdAs(
      DASHBOARD_INDEX.getIndexName(),
      "dashboard-2", // dashboard with empty filter list
      DashboardDefinitionRestDto.class
    )).isPresent()
      .map(BaseDashboardDefinitionDto::getAvailableFilters)
      .contains(EXPECTED_FILTERS_DASHBOARD_2);
    assertThat(getDocumentOfIndexByIdAs(
      DASHBOARD_INDEX.getIndexName(),
      "dashboard-3", // dashboard with null filter list
      DashboardDefinitionRestDto.class
    )).isPresent()
      .map(BaseDashboardDefinitionDto::getAvailableFilters)
      .isEmpty();
  }

  private static DashboardFilterDto<?> createDashboardInstanceEndDateFilter() {
    DashboardInstanceEndDateFilterDto filterDto = new DashboardInstanceEndDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(null));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardInstanceStartDateFilter() {
    DashboardInstanceStartDateFilterDto filterDto = new DashboardInstanceStartDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(null));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStateFilter() {
    DashboardStateFilterDto filterDto = new DashboardStateFilterDto();
    filterDto.setData(new DashboardStateFilterDataDto(null));
    return filterDto;
  }
}
