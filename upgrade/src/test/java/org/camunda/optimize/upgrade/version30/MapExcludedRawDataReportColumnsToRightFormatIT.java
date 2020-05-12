/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import com.google.common.base.Functions;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class MapExcludedRawDataReportColumnsToRightFormatIT extends AbstractUpgradeIT {
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
      TIMESTAMP_BASED_IMPORT_INDEX,
      IMPORT_INDEX_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/3.0/report_data/30-raw-data-report-with-excluded-columns-bulk");
  }

  @Test
  public void excludedColumnsAreMigrated_forProcessReports() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleProcessReportDefinitionDto> processReports = getAllDocumentsOfIndex(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionDto.class
    );
    assertThat(processReports)
      .hasSize(3)
      .extracting(SingleProcessReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getConfiguration)
      .extracting(SingleReportConfigurationDto::getExcludedColumns)
      .allSatisfy(list -> assertThat(list).doesNotHaveDuplicates())
      .flatExtracting(Functions.identity())
      .allSatisfy(col -> assertThat(col).doesNotContain("__"))
      .containsExactlyInAnyOrder(
        "processInstanceId",
        "businessKey",
        "variable:approver",
        "variable:approver",
        "variable:approver",
        "variable:creditor",
        "variable:creditor"
      );
  }

  @Test
  public void excludedColumnsAreMigrated_forDecisionReports() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleDecisionReportDefinitionDto> decisionReports = getAllDocumentsOfIndex(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      SingleDecisionReportDefinitionDto.class
    );

    assertThat(decisionReports)
      .hasSize(3)
      .extracting(SingleDecisionReportDefinitionDto::getData)
      .extracting(DecisionReportDataDto::getConfiguration)
      .extracting(SingleReportConfigurationDto::getExcludedColumns)
      .allSatisfy(list -> assertThat(list).doesNotHaveDuplicates())
      .flatExtracting(Functions.identity())
      .allSatisfy(col -> assertThat(col).doesNotContain("__"))
      .containsExactlyInAnyOrder(
        "input:clause1",
        "input:clause1",
        "input:clause1",
        "output:clause3",
        "output:clause3",
        "output:clause3",
        "input:InputClause_15qmk0v"
      );
  }

}
