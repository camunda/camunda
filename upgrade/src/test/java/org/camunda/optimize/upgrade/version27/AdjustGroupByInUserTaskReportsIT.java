/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom27To30;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AdjustGroupByInUserTaskReportsIT extends AbstractUpgradeIT {

  private static final int EXPECTED_NUMBER_OF_PROCESS_REPORTS = 4;

  private static final String USER_TASK_DURATION_REPORT_ID = "85c9dcf0-c88e-402c-b765-233c3958b6c5";
  private static final String USER_TASK_COUNT_REPORT_ID = "2cb4eeaf-cb0b-4d6b-8ebf-7abbb9b47ee6";
  private static final String RAW_DATA_REPORT_ID = "ef7e4278-d15d-42da-87c6-4267ec4e5803";
  private static final String FLOW_NODE_REPORT_ID = "174ba9c1-4fa0-4730-89af-3948b879c2a4";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(ALL_INDEXES);
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/2.7/27-single-process-report-bulk");
  }

  @Test
  public void processReportsHaveExpectedSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllProcessReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName());

    // then
    assertThat(allProcessReports.size()).isEqualTo(EXPECTED_NUMBER_OF_PROCESS_REPORTS);

    // when
    SingleProcessReportDefinitionDto report =
      filterOutReport(USER_TASK_DURATION_REPORT_ID, allProcessReports);

    // then
    assertThat(report.getData().getGroupBy().getType()).isEqualTo(ProcessGroupByType.USER_TASKS);

    // when
    report =
      filterOutReport(USER_TASK_COUNT_REPORT_ID, allProcessReports);

    // then
    assertThat(report.getData().getGroupBy().getType()).isEqualTo(ProcessGroupByType.USER_TASKS);

    // when
    report =
      filterOutReport(RAW_DATA_REPORT_ID, allProcessReports);

    // then
    assertThat(report.getData().getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    // when
    report =
      filterOutReport(FLOW_NODE_REPORT_ID, allProcessReports);

    // then
    assertThat(report.getData().getGroupBy().getType()).isEqualTo(ProcessGroupByType.FLOW_NODES);
  }

  private SingleProcessReportDefinitionDto filterOutReport(final String reportId,
                                                           final List<SingleProcessReportDefinitionDto> allProcessReports) {
    return allProcessReports.stream()
      .filter(r -> reportId.equals(r.getId()))
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("The report should be available!"));
  }
}
