/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
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

public class ReportMigrationIT extends AbstractUpgradeIT {
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

    executeBulk("steps/3.0/report_data/30-report-bulk-no-labels");
  }

  @Test
  public void reportsAreMigratedNoDataLost() {
    // given
    final List<SingleProcessReportDefinitionDto> processReportsBeforeUpgrade = getProcessReports();
    final List<SingleDecisionReportDefinitionDto> decisionReportsBeforeUpgrade = getDecisionReports();

    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getProcessReports()).isEqualTo(processReportsBeforeUpgrade);
    assertThat(getDecisionReports()).isEqualTo(decisionReportsBeforeUpgrade);
  }

  private List<SingleDecisionReportDefinitionDto> getDecisionReports() {
    return getAllDocumentsOfIndexAs(SINGLE_DECISION_REPORT_INDEX_NAME, SingleDecisionReportDefinitionDto.class);
  }

  private List<SingleProcessReportDefinitionDto> getProcessReports() {
    return getAllDocumentsOfIndexAs(SINGLE_PROCESS_REPORT_INDEX_NAME, SingleProcessReportDefinitionDto.class);
  }

}
