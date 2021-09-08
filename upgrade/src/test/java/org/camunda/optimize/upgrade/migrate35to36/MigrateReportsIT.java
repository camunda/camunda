/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.MeasureVisualizationsDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade35To36PlanFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class MigrateReportsIT extends AbstractUpgrade35IT {

  @SuppressWarnings(UNCHECKED_CAST)
  @SneakyThrows
  @ParameterizedTest
  @MethodSource("reportMigrationsScenarios")
  public void measureVisualizationIsInitialized(final String bulkFile, final String indexName) {
    // given
    executeBulk("steps/3.5/report/" + bulkFile);
    final UpgradePlan upgradePlan = new Upgrade35To36PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndex(indexName))
      .hasSize(1)
      .allSatisfy(report -> {
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        assertThat(reportConfig)
          .containsEntry(SingleReportConfigurationDto.Fields.stackedBar, false);
        final Map<String, Object> measureVisualizationConfig =
          (Map<String, Object>) reportConfig.get(SingleReportConfigurationDto.Fields.measureVisualizations);
        assertThat(measureVisualizationConfig).isNotNull();
        assertThat(measureVisualizationConfig)
          .containsEntry(MeasureVisualizationsDto.Fields.frequency, ReportConstants.BAR_VISUALIZATION);
        assertThat(measureVisualizationConfig)
          .containsEntry(MeasureVisualizationsDto.Fields.duration, ReportConstants.LINE_VISUALIZATION);
      });
  }

  public static Stream<Arguments> reportMigrationsScenarios() {
    return Stream.of(
      Arguments.of("35-process-reports.json", SINGLE_PROCESS_REPORT_INDEX.getIndexName()),
      Arguments.of("35-decision-reports.json", SINGLE_DECISION_REPORT_INDEX.getIndexName())
    );
  }
}