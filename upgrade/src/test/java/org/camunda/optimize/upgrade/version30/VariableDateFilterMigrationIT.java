/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class VariableDateFilterMigrationIT extends AbstractUpgrade30IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.0/report_data/30-report-with-date-filters-bulk");
  }

  @SneakyThrows
  @Test
  public void reportFiltersAreMigrated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleProcessReportDefinitionDto> singleProcessReportDefinitionDtos = getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionDto.class
    );
    assertThat(singleProcessReportDefinitionDtos)
      .extracting(ReportDefinitionDto::getData)
      .flatExtracting(ProcessReportDataDto::getFilter)
      .filteredOn(filter -> filter instanceof VariableFilterDto)
      .extracting(ProcessFilterDto::getData)
      .filteredOn(filterData -> filterData instanceof DateVariableFilterDataDto)
      .hasOnlyOneElementSatisfying(filterData -> {
        final DateFilterDataDto<?> dateFilterDataDto = ((DateVariableFilterDataDto) filterData).getData();
        assertThat(dateFilterDataDto).isInstanceOf(FixedDateFilterDataDto.class);
        assertThat(dateFilterDataDto.getType()).isEqualTo(DateFilterType.FIXED);
        assertThat(dateFilterDataDto.isIncludeUndefined()).isEqualTo(false);
        assertThat(dateFilterDataDto.isExcludeUndefined()).isEqualTo(false);
      });

    final List<SingleDecisionReportDefinitionDto> decisionReports = getAllDocumentsOfIndexAs(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      SingleDecisionReportDefinitionDto.class
    );
    assertThat(decisionReports)
      .extracting(ReportDefinitionDto::getData)
      .flatExtracting(DecisionReportDataDto::getFilter)
      .filteredOn(filter -> filter instanceof InputVariableFilterDto)
      .extracting(DecisionFilterDto::getData)
      .filteredOn(filterData -> filterData instanceof DateVariableFilterDataDto)
      .hasOnlyOneElementSatisfying(filterData -> {
        final DateFilterDataDto<?> dateFilterDataDto = ((DateVariableFilterDataDto) filterData).getData();
        assertThat(dateFilterDataDto).isInstanceOf(FixedDateFilterDataDto.class);
        assertThat(dateFilterDataDto.getType()).isEqualTo(DateFilterType.FIXED);
        assertThat(dateFilterDataDto.isIncludeUndefined()).isEqualTo(false);
        assertThat(dateFilterDataDto.isExcludeUndefined()).isEqualTo(false);
      });
    assertThat(decisionReports)
      .extracting(ReportDefinitionDto::getData)
      .flatExtracting(DecisionReportDataDto::getFilter)
      .filteredOn(filter -> filter instanceof OutputVariableFilterDto)
      .extracting(DecisionFilterDto::getData)
      .filteredOn(filterData -> filterData instanceof DateVariableFilterDataDto)
      .hasOnlyOneElementSatisfying(filterData -> {
        final DateFilterDataDto<?> dateFilterDataDto = ((DateVariableFilterDataDto) filterData).getData();
        assertThat(dateFilterDataDto).isInstanceOf(FixedDateFilterDataDto.class);
        assertThat(dateFilterDataDto.getType()).isEqualTo(DateFilterType.FIXED);
        assertThat(dateFilterDataDto.isIncludeUndefined()).isEqualTo(false);
        assertThat(dateFilterDataDto.isExcludeUndefined()).isEqualTo(false);
      });
  }

}
