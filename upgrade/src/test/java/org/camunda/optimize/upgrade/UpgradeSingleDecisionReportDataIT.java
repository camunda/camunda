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
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25.getMigrate25ReportViewStructureScript;
import static org.hamcrest.CoreMatchers.is;
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

    initSchema(Lists.newArrayList(METADATA_TYPE, DECISION_DEFINITION_TYPE, new SingleDecisionReportType()));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/24-single-decision-report-bulk");
  }


  @Test
  public void rawDataViewOperationFieldHasBeenMovedToViewPropertyField() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleDecisionReportDefinitionDto report = getSingleDecisionReportDefinitionDataById(REPORT_VERSION_24_ID);

    assertThat(report.getData().getView().getProperty(), is(DecisionViewProperty.RAW_DATA));
  }


  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpdateDataStep(
        SINGLE_DECISION_REPORT_TYPE.getType(),
        QueryBuilders.matchAllQuery(),
        getMigrate25ReportViewStructureScript()
      ))
      .build();
  }

  private SingleDecisionReportDefinitionDto getSingleDecisionReportDefinitionDataById(final String id) throws
                                                                                                       IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_DECISION_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleDecisionReportDefinitionDto.class
    );
  }

}
