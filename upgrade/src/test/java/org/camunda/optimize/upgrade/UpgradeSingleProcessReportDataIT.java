/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25.buildUpgradePlan;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class UpgradeSingleProcessReportDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";

  private static final ProcessDefinitionType PROCESS_DEFINITION_TYPE = new ProcessDefinitionType();
  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  private static final String REPORT_VERSION_24_ID = "1aedc93f-7b8d-45a6-8a3c-cd14f4847152";


  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      new SingleDecisionReportType(),
      new SingleProcessReportType(),
      new ProcessInstanceType()
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/24-single-process-report-bulk");
  }


  @Test
  public void reportConfigurationHasHiddenNodesField() throws Exception {
    //given
    UpgradePlan upgradePlan = buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report = getSingleProcessReportDefinitionDataById();

    assertThat(report.getData().getConfiguration().getHiddenNodes(), notNullValue());
  }

  @Test
  public void reportConfigurationHasExecutionStateField() throws Exception {
    //given
    UpgradePlan upgradePlan = buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleProcessReportDefinitionDto report = getSingleProcessReportDefinitionDataById();

    assertThat(report.getData().getConfiguration().getFlowNodeExecutionState(), notNullValue());
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE.getType());
  }

  private SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDataById() throws
                                                                                      IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_PROCESS_REPORT_TYPE.getType(),
                     UpgradeSingleProcessReportDataIT.REPORT_VERSION_24_ID
      ), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class
    );
  }

}
