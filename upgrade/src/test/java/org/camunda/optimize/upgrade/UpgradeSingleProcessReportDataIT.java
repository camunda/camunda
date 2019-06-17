/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class UpgradeSingleProcessReportDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";

  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  private static final String REPORT_VERSION_24_ID = "1aedc93f-7b8d-45a6-8a3c-cd14f4847152";
  private static final String REPORT_VERSION_24_ID_IDLE_USER_TASK_REPORT = "60069f78-8f30-47ba-ba90-d983bc53e427";
  private static final String REPORT_VERSION_24_ID_WORK_USER_TASK_REPORT = "11238dca-e3d8-4f68-b9c2-2eaee39dde83";
  private static final String REPORT_VERSION_24_ID_TOTAL_USER_TASK_REPORT = "13773df2-8eff-43e4-b65d-cf83975d9681";


  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      new SingleDecisionReportType(),
      new ProcessDefinitionType(),
      new SingleProcessReportType(),
      new ProcessInstanceType()
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/24-single-process-report-bulk");
  }


  @Test
  public void reportConfigurationHasHiddenNodesField() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report = getSingleProcessReportDefinitionDataById(REPORT_VERSION_24_ID);

    assertThat(report.getData().getConfiguration().getHiddenNodes(), notNullValue());
  }

  @Test
  public void reportConfigurationHasExecutionStateField() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report = getSingleProcessReportDefinitionDataById(REPORT_VERSION_24_ID);

    assertThat(report.getData().getConfiguration().getFlowNodeExecutionState(), notNullValue());
  }

  @Test
  public void moveUserTaskDurationTimeFromReportViewToConfiguration_totalTime() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report =
      getSingleProcessReportDefinitionDataById(REPORT_VERSION_24_ID_TOTAL_USER_TASK_REPORT);

    assertThat(report.getData().getConfiguration().getUserTaskDurationTime(), is(UserTaskDurationTime.TOTAL));
    assertThat(report.getData().getView().getProperty(), is(ProcessViewProperty.DURATION));
  }

  @Test
  public void moveUserTaskDurationTimeFromReportViewToConfiguration_idleTime() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report =
      getSingleProcessReportDefinitionDataById(REPORT_VERSION_24_ID_IDLE_USER_TASK_REPORT);

    assertThat(report.getData().getConfiguration().getUserTaskDurationTime(), is(UserTaskDurationTime.IDLE));
    assertThat(report.getData().getView().getProperty(), is(ProcessViewProperty.DURATION));
  }

  @Test
  public void moveUserTaskDurationTimeFromReportViewToConfiguration_workTime() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report =
      getSingleProcessReportDefinitionDataById(REPORT_VERSION_24_ID_WORK_USER_TASK_REPORT);

    assertThat(report.getData().getConfiguration().getUserTaskDurationTime(), is(UserTaskDurationTime.WORK));
    assertThat(report.getData().getView().getProperty(), is(ProcessViewProperty.DURATION));
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE.getType());
  }

  private SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDataById(String reportId) throws
                                                                                                     IOException {
    final GetResponse reportResponse =
      restClient.get(
        new GetRequest(getReportIndexAlias(), SINGLE_PROCESS_REPORT_TYPE.getType(), reportId),
        RequestOptions.DEFAULT
      );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class
    );
  }

}
