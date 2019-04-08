/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleDecisionReportSettingsStep;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildDecisionDefinitionXmlByKeyAndVersionMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultSingleReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class UpgradeSingleDecisionReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final DecisionDefinitionType DECISION_DEFINITION_TYPE = new DecisionDefinitionType();
  private static final AbstractReportType SINGLE_DECISION_REPORT_TYPE = new SingleDecisionReportType();

  private static final String DECISION_DEFINITION_INVOICE_KEY = "invoiceClassification";
  private static final String EMPTY_REPORT_ID = "66955a8b-7ac2-4f60-b9a2-c1c40273998f";
  private static final String REPORT_VERSION_ALL_ID = "683633bb-4c71-4c3e-a6b9-9d870f064ae9";
  private static final String REPORT_VERSION_1_ID = "a4da6048-3a32-4cb3-9706-a713dcfe6647";

  private Map<String, Map<String, String>> decisionDefinitionXmlByKeyAndVersion;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, DECISION_DEFINITION_TYPE, SINGLE_DECISION_REPORT_TYPE));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/23_decision_definition-bulk");
    executeBulk("steps/configuration_upgrade/23-single-decision-report-bulk");

    decisionDefinitionXmlByKeyAndVersion = buildDecisionDefinitionXmlByKeyAndVersionMap(new ConfigurationService());
  }

  @Test
  public void xmlSet() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleReportConfigurationDto configurationVersionAll = getSingleDecisionReportDefinitionConfigurationById(
      REPORT_VERSION_ALL_ID
    );
    assertThat(configurationVersionAll.getXml(), is(notNullValue()));
    assertThat(configurationVersionAll.getXml(), is(getInvoiceClassificationXmlByVersion("2")));

    final SingleReportConfigurationDto configurationVersion1 = getSingleDecisionReportDefinitionConfigurationById(
      REPORT_VERSION_1_ID
    );
    assertThat(configurationVersion1.getXml(), is(notNullValue()));
    assertThat(configurationVersion1.getXml(), is(getInvoiceClassificationXmlByVersion("1")));

    final SingleReportConfigurationDto configurationEmpty = getSingleDecisionReportDefinitionConfigurationById(
      EMPTY_REPORT_ID
    );
    assertThat(configurationEmpty.getXml(), is(nullValue()));
  }

  public String getInvoiceClassificationXmlByVersion(final String version) {
    return decisionDefinitionXmlByKeyAndVersion.get(DECISION_DEFINITION_INVOICE_KEY).get(version);
  }


  @Test
  public void columnOrderStillPresent() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final SingleReportConfigurationDto configuration = getSingleDecisionReportDefinitionConfigurationById(
      REPORT_VERSION_ALL_ID
    );
    assertThat(configuration.getColumnOrder().getInputVariables().size(), is(2));
    assertThat(configuration.getColumnOrder().getOutputVariables().size(), is(1));
    assertThat(configuration.getColumnOrder().getInstanceProps().size(), is(5));
  }


  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeSingleDecisionReportSettingsStep(
        getDefaultSingleReportConfigurationAsMap(),
        decisionDefinitionXmlByKeyAndVersion
      ))
      .build();
  }

  private SingleReportConfigurationDto getSingleDecisionReportDefinitionConfigurationById(final String id) throws
                                                                                                     IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_DECISION_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleDecisionReportDefinitionDto.class
    ).getData().getConfiguration();
  }

}
