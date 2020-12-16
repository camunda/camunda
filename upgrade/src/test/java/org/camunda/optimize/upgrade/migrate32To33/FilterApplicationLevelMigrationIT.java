/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeFrom32To33Factory;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterApplicationLevelMigrationIT extends AbstractUpgrade32IT {

  @SneakyThrows
  @Test
  public void allReportFiltersAreCorrectlyMigrated() {
    // given
    executeBulk("steps/3.2/reports/32-decision-report-filters-bulk");
    executeBulk("steps/3.2/reports/32-process-report-filters-bulk");
    final SingleDecisionReportDefinitionRequestDto decisionReportBeforeUpdate = getDecisionReportWithId("decision-1");

    assertThat(getProcessReportWithId("non-user-task-process-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-other-thing-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-user-task-report-no-filters")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).isEmpty());
    assertThat(getProcessReportWithId("user-task-view-by-user-task-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-user-task-start-date-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-user-task-end-date-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-duration-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-candidateGroup-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));
    assertThat(getProcessReportWithId("user-task-view-by-assignee-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(filter.getFilterLevel()).isNull()));

    final UpgradePlan upgradePlan = UpgradeFrom32To33Factory.createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getDecisionReportWithId("decision-1").getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSameSizeAs(decisionReportBeforeUpdate.getData().getFilter()));
    assertThat(getProcessReportWithId("non-user-task-process-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(FilterApplicationLevel.INSTANCE.equals(filter.getFilterLevel()))));
    assertThat(getProcessReportWithId("user-task-view-by-other-thing-report")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).hasSize(18)
        .allSatisfy(filter -> assertThat(FilterApplicationLevel.INSTANCE.equals(filter.getFilterLevel()))));
    assertThat(getProcessReportWithId("user-task-view-by-user-task-report-no-filters")).isPresent()
      .hasValueSatisfying(report -> assertThat(report.getData().getFilter()).isEmpty());
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-user-task-report");
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-user-task-start-date-report");
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-user-task-end-date-report");
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-duration-report");
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-candidateGroup-report");
    assertFiltersMigratedCorrectlyForReport("user-task-view-by-assignee-report");
  }

  private SingleDecisionReportDefinitionRequestDto getDecisionReportWithId(final String reportId) {
    return getDocumentOfIndexByIdAs(new SingleDecisionReportIndex().getIndexName(), reportId,
                                    SingleDecisionReportDefinitionRequestDto.class
    )
      .orElseThrow(() -> new OptimizeIntegrationTestException("Cannot find decision report"));
  }

  private void assertFiltersMigratedCorrectlyForReport(final String reportId) {
    assertThat(getProcessReportWithId(reportId)).isPresent()
      .hasValueSatisfying(report -> {
        final List<ProcessFilterDto<?>> filters = report.getData().getFilter();
        assertThat(filters).hasSize(18);
        assertThat(filters)
          .filteredOn(filter -> FilterApplicationLevel.INSTANCE.equals(filter.getFilterLevel())).hasSize(16);
        assertThat(filters)
          .filteredOn(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel())).hasSize(2);
      });
  }

  private Optional<SingleProcessReportDefinitionRequestDto> getProcessReportWithId(final String reportId) {
    return getDocumentOfIndexByIdAs(
      new SingleProcessReportIndex().getIndexName(), reportId, SingleProcessReportDefinitionRequestDto.class);
  }

}
