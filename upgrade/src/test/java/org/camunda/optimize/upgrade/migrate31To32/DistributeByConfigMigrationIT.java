/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class DistributeByConfigMigrationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.1/reports/31-distributed-process-report-bulk");
    executeBulk("steps/3.1/reports/31-distributed-decision-report-bulk");
  }

  @Test
  public void migrateDistributeBy_none() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<DistributedByType> distributedByDtos = getProcessDistributedByForReportId("process-report-none");
    distributedByDtos.addAll(getAllDecisionDistributedBy()); // decision reports are all distributed by none
    assertThat(distributedByDtos)
      .isNotEmpty()
      .containsOnly(DistributedByType.NONE);
  }

  @Test
  public void migrateDistributeBy_userTask() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<DistributedByType> distributedByDtos = getProcessDistributedByForReportId("process-report-user-task");
    assertThat(distributedByDtos)
      .isNotEmpty()
      .containsOnly(DistributedByType.USER_TASK);
  }

  @Test
  public void migrateDistributeBy_flowNode() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<DistributedByType> distributedByDtos = getProcessDistributedByForReportId("process-report-flow-node");
    assertThat(distributedByDtos)
      .isNotEmpty()
      .containsOnly(DistributedByType.FLOW_NODE);
  }

  @Test
  public void migrateDistributeBy_assignee() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<DistributedByType> distributedByDtos = getProcessDistributedByForReportId("process-report-assignee");
    assertThat(distributedByDtos)
      .isNotEmpty()
      .containsOnly(DistributedByType.ASSIGNEE);
  }

  @Test
  public void migrateDistributeBy_candidateGroup() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<DistributedByType> distributedByDtos = getProcessDistributedByForReportId(
      "process-report-candidate-group");
    assertThat(distributedByDtos)
      .isNotEmpty()
      .containsOnly(DistributedByType.CANDIDATE_GROUP);
  }

  private List<DistributedByType> getProcessDistributedByForReportId(final String reportId) {
    return getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionDto.class
    ).stream()
      .filter(report -> reportId.equals(report.getId()))
      .map(SingleProcessReportDefinitionDto::getData)
      .map(ProcessReportDataDto::getConfiguration)
      .map(SingleReportConfigurationDto::getDistributedBy)
      .map(ProcessDistributedByDto::getType)
      .collect(toList());
  }

  private List<DistributedByType> getAllDecisionDistributedBy() {
    return getAllDocumentsOfIndexAs(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      SingleDecisionReportDefinitionDto.class
    ).stream()
      .map(SingleDecisionReportDefinitionDto::getData)
      .map(DecisionReportDataDto::getConfiguration)
      .map(SingleReportConfigurationDto::getDistributedBy)
      .map(ProcessDistributedByDto::getType)
      .collect(toList());
  }

}
