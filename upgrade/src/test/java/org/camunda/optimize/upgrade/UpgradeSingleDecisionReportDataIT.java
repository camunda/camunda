/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class UpgradeSingleDecisionReportDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";

  private static final DecisionDefinitionType DECISION_DEFINITION_TYPE = new DecisionDefinitionType();
  private static final AbstractReportType SINGLE_DECISION_REPORT_TYPE = new SingleDecisionReportType();

  private static final String REPORT_VERSION_24_ID = "8fb66435-1d7d-41fa-b95e-e3ba7536d218";


  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      DECISION_DEFINITION_TYPE,
      new SingleDecisionReportType(),
      new SingleProcessReportType(),
      new ProcessInstanceType()
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/24-single-decision-report-bulk");
  }


  @Test
  public void rawDataViewOperationFieldHasBeenMovedToViewPropertyField() throws Exception {
    //given
    UpgradePlan upgradePlan = buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleDecisionReportDefinitionDto report = getSingleDecisionReportDefinitionDataById();

    assertThat(report.getData().getView().getProperty(), is(DecisionViewProperty.RAW_DATA));
  }

  @Test
  public void reportConfigurationHasHiddenNodesField() throws Exception {
    //given
    UpgradePlan upgradePlan = buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleDecisionReportDefinitionDto report = getSingleDecisionReportDefinitionDataById();

    assertThat(report.getData().getConfiguration().getHiddenNodes(), notNullValue());
  }

  @Test
  public void reportConfigurationHasExecutionStateField() throws Exception {
    //given
    UpgradePlan upgradePlan = buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleDecisionReportDefinitionDto report = getSingleDecisionReportDefinitionDataById();

    assertThat(report.getData().getConfiguration().getFlowNodeExecutionState(), notNullValue());
  }


  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE.getType());
  }

  private SingleDecisionReportDefinitionDto getSingleDecisionReportDefinitionDataById() throws
                                                                                        IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_DECISION_REPORT_TYPE.getType(),
                     UpgradeSingleDecisionReportDataIT.REPORT_VERSION_24_ID
      ), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleDecisionReportDefinitionDto.class
    );
  }

}
