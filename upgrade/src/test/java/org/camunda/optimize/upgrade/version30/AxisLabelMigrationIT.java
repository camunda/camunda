/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class AxisLabelMigrationIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "3.0.0";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/3.0/30-report-bulk");
  }

  @SuppressWarnings("unchecked")
  @SneakyThrows
  @Test
  public void axisLabelsAreMigrated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleProcessReportDefinitionDto> singleProcessReportDefinitionDtos = getAllDocumentsOfIndex(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionDto.class
    );
    assertThat(singleProcessReportDefinitionDtos)
      .hasSize(2)
      .extracting(ReportDefinitionDto::getData)
      .extracting(SingleReportDataDto::getConfiguration)
      .extracting(SingleReportConfigurationDto::getXLabel, SingleReportConfigurationDto::getYLabel)
      .containsExactlyInAnyOrder(
        // one report containing labels from Optimize <2.5.0
        Tuple.tuple("x", "y"),
        // one report without labels from Optimize >=2.5.0, where they were not stored
        Tuple.tuple("", "")
      );

    assertThat(getAllDocumentsOfIndex(SINGLE_DECISION_REPORT_INDEX_NAME, SingleDecisionReportDefinitionDto.class))
      .hasSize(2)
      .extracting(ReportDefinitionDto::getData)
      .extracting(SingleReportDataDto::getConfiguration)
      .extracting(SingleReportConfigurationDto::getXLabel, SingleReportConfigurationDto::getYLabel)
      .containsExactlyInAnyOrder(
        // one report containing labels from Optimize <2.5.0
        Tuple.tuple("x", "y"),
        // one report without labels from Optimize >=2.5.0, where they were not stored
        Tuple.tuple("", "")
      );

    assertThat(getAllDocumentsOfIndex(COMBINED_REPORT_INDEX_NAME, CombinedReportDefinitionDto.class))
      .hasSize(2)
      .extracting(ReportDefinitionDto::getData)
      .extracting(CombinedReportDataDto::getConfiguration)
      .extracting(CombinedReportConfigurationDto::getXLabel, CombinedReportConfigurationDto::getYLabel)
      .containsExactly(
        // one report containing labels from Optimize <2.5.0
        Tuple.tuple("x", "y"),
        // one report without labels from Optimize >=2.5.0, where they were not stored
        Tuple.tuple("", "")
      );
  }

}
