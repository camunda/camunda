/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class TableColumnConfigMigrationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.1/reports/31-process-report-bulk");
    executeBulk("steps/3.1/reports/31-decision-report-bulk");
  }

  @Test
  public void migrateColumnsSettingsToTableColumnDto_WithIncludeAndExcludeColumns() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();
    final List<String> expectedExcluded = Lists.newArrayList("Excluded1", "Excluded2");
    final List<String> expectedIncluded = Lists.newArrayList("Included1", "Included2");
    final TableColumnDto expectedTableColumnDto = TableColumnDto.builder()
      .excludedColumns(expectedExcluded)
      .includedColumns(expectedIncluded)
      .build();

    // when
    upgradePlan.execute();

    // then
    final List<TableColumnDto> allTableColumnConfigs = getAllTableColumnConfigs("report-with-exclude-and-include-cols");
    assertThat(allTableColumnConfigs)
      .isNotEmpty()
      .allMatch(tableColumnDto -> tableColumnDto.equals(expectedTableColumnDto));
  }

  @Test
  public void migrateColumnsSettingsToTableColumnDto_WithEmptyExcludeColumns() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();
    final List<String> expectedExcluded = Lists.emptyList();
    final List<String> expectedIncluded = Lists.newArrayList("Included1", "Included2");
    final TableColumnDto expectedTableColumnDto = TableColumnDto.builder()
      .excludedColumns(expectedExcluded)
      .includedColumns(expectedIncluded)
      .build();

    // when
    upgradePlan.execute();

    // then
    final List<TableColumnDto> allTableColumnConfigs = getAllTableColumnConfigs("report-with-empty-exclude");
    assertThat(allTableColumnConfigs)
      .isNotEmpty()
      .allMatch(tableColumnDto -> tableColumnDto.equals(expectedTableColumnDto));
  }

  @Test
  public void migrateColumnsSettingsToTableColumnDto_WithEmptyIncludeColumns() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();
    final List<String> expectedExcluded = Lists.newArrayList("Excluded1", "Excluded2");
    final List<String> expectedIncluded = Lists.emptyList();
    final TableColumnDto expectedTableColumnDto = TableColumnDto.builder()
      .excludedColumns(expectedExcluded)
      .includedColumns(expectedIncluded)
      .build();

    // when
    upgradePlan.execute();

    // then
    final List<TableColumnDto> allTableColumnConfigs = getAllTableColumnConfigs("report-with-empty-include");
    assertThat(allTableColumnConfigs)
      .isNotEmpty()
      .allMatch(tableColumnDto -> tableColumnDto.equals(expectedTableColumnDto));
  }

  @Test
  public void migrateColumnsSettingsToTableColumnDto_WithNullExcludeColumns() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();
    final List<String> expectedExcluded = Lists.emptyList();
    final List<String> expectedIncluded = Lists.newArrayList("Included1", "Included2");
    final TableColumnDto expectedTableColumnDto = TableColumnDto.builder()
      .excludedColumns(expectedExcluded)
      .includedColumns(expectedIncluded)
      .build();

    // when
    upgradePlan.execute();

    // then
    final List<TableColumnDto> allTableColumnConfigs = getAllTableColumnConfigs("report-with-null-exclude");
    assertThat(allTableColumnConfigs)
      .isNotEmpty()
      .allMatch(tableColumnDto -> tableColumnDto.equals(expectedTableColumnDto));
  }

  @Test
  public void migrateColumnsSettingsToTableColumnDto_WithNullIncludeColumns() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();
    final List<String> expectedExcluded = Lists.newArrayList("Excluded1", "Excluded2");
    final List<String> expectedIncluded = Lists.emptyList();
    final TableColumnDto expectedTableColumnDto = TableColumnDto.builder()
      .excludedColumns(expectedExcluded)
      .includedColumns(expectedIncluded)
      .build();

    // when
    upgradePlan.execute();

    // then
    final List<TableColumnDto> allTableColumnConfigs = getAllTableColumnConfigs("report-with-null-include");
    assertThat(allTableColumnConfigs)
      .isNotEmpty()
      .allMatch(tableColumnDto -> tableColumnDto.equals(expectedTableColumnDto));
  }

  private List<TableColumnDto> getAllTableColumnConfigs(final String reportId) {
    final List<TableColumnDto> allTableCols = getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionDto.class
    ).stream()
      .filter(report -> reportId.equals(report.getId()))
      .map(SingleProcessReportDefinitionDto::getData)
      .map(ProcessReportDataDto::getConfiguration)
      .map(SingleReportConfigurationDto::getTableColumns)
      .collect(toList());

    allTableCols.addAll(
      getAllDocumentsOfIndexAs(
        SINGLE_DECISION_REPORT_INDEX_NAME,
        SingleDecisionReportDefinitionDto.class
      ).stream()
        .filter(report -> reportId.equals(report.getId()))
        .map(SingleDecisionReportDefinitionDto::getData)
        .map(DecisionReportDataDto::getConfiguration)
        .map(SingleReportConfigurationDto::getTableColumns)
        .collect(toList())
    );

    return allTableCols;
  }
}
