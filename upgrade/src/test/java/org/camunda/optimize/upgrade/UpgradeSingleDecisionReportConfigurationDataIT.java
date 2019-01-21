package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleDecisionReportSettingsFrom23Step;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UpgradeSingleDecisionReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final AbstractReportType SINGLE_DECISION_REPORT_TYPE = new SingleDecisionReportType();

  private static final String REPORT_ID = "683633bb-4c71-4c3e-a6b9-9d870f064ae9";

  private ObjectMapper objectMapper;

  @Before
  public void init() throws Exception {
    objectMapper = new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(),
      new ConfigurationService()
    ).createOptimizeMapper();

    initClient();
    cleanAllDataFromElasticsearch();

    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      new ConfigurationService(),
      Lists.newArrayList(SINGLE_DECISION_REPORT_TYPE),
      objectMapper
    );
    elasticSearchSchemaManager.initializeSchema(restClient);

    addVersionToElasticsearch(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/23-single-decision-report-bulk");

    createEmptyEnvConfig();
  }

  @Test
  public void columnOrderStillPresent() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleDecisionReportDefinitionConfigurationById(REPORT_ID);
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
      .addUpgradeStep(new UpgradeSingleDecisionReportSettingsFrom23Step(getDefaultReportConfigurationAsMap()))
      .build();
  }

  private ReportConfigurationDto getSingleDecisionReportDefinitionConfigurationById(final String id) throws IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_DECISION_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class
    ).getData().getConfiguration();
  }

}
