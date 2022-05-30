/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEntityEventProperties;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelHeartbeatProperties;
import org.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

public class MixpanelDataServiceIT extends AbstractIT {

  private static final String CLUSTER_ID = "IT-cluster";
  private static final String STAGE = "IT";
  private static final String ORGANIZATION_ID = "orgId";

  @Test
  public void heartbeatEventData() {
    // given
    final MixpanelConfiguration mixpanelConfiguration = getMixpanelConfiguration();
    mixpanelConfiguration.getProperties().setStage(STAGE);
    mixpanelConfiguration.getProperties().setOrganizationId(ORGANIZATION_ID);
    mixpanelConfiguration.getProperties().setClusterId(CLUSTER_ID);

    reportClient.createEmptySingleProcessReport();
    createManagementReport();
    final String collectionId = collectionClient.createNewCollection();
    final String reportInCollectionId = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    reportClient.createEmptySingleDecisionReport();
    final String dashboardId = dashboardClient.createEmptyDashboard();
    createManagementDashboard();
    alertClient.createAlertForReport(reportInCollectionId);
    final ReportShareRestDto reportShare = new ReportShareRestDto();
    reportShare.setReportId(reportInCollectionId);
    sharingClient.createReportShareResponse(reportShare);
    final DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
    dashboardShare.setDashboardId(dashboardId);
    sharingClient.createDashboardShareResponse(dashboardShare);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final MixpanelHeartbeatProperties mixpanelHeartbeatProperties =
      getMixpanelDataService().getMixpanelHeartbeatProperties();

    // then
    assertThat(mixpanelHeartbeatProperties.getTime()).isLessThanOrEqualTo(System.currentTimeMillis());
    assertThat(mixpanelHeartbeatProperties.getDistinctId()).isEmpty();
    assertThat(mixpanelHeartbeatProperties.getInsertId()).isNotEmpty();
    assertThat(mixpanelHeartbeatProperties.getProduct()).isEqualTo("optimize");
    assertThat(mixpanelHeartbeatProperties.getStage()).isEqualTo(STAGE);
    assertThat(mixpanelHeartbeatProperties.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
    assertThat(mixpanelHeartbeatProperties.getClusterId()).isEqualTo(CLUSTER_ID);
    // The management report is not included in the result
    assertThat(mixpanelHeartbeatProperties.getProcessReportCount()).isEqualTo(2);
    assertThat(mixpanelHeartbeatProperties.getDecisionReportCount()).isEqualTo(1);
    // The management dashboard is not included in the result
    assertThat(mixpanelHeartbeatProperties.getDashboardCount()).isEqualTo(1);
    assertThat(mixpanelHeartbeatProperties.getReportShareCount()).isEqualTo(1);
    assertThat(mixpanelHeartbeatProperties.getDashboardShareCount()).isEqualTo(1);
    assertThat(mixpanelHeartbeatProperties.getAlertCount()).isEqualTo(1);
  }

  @Test
  public void entityEventData() {
    // given
    final MixpanelConfiguration mixpanelConfiguration = getMixpanelConfiguration();
    mixpanelConfiguration.getProperties().setStage(STAGE);
    mixpanelConfiguration.getProperties().setOrganizationId(ORGANIZATION_ID);
    mixpanelConfiguration.getProperties().setClusterId(CLUSTER_ID);

    // when
    final String entityId = "id";
    final MixpanelEntityEventProperties mixpanelEntityEventProperties =
      getMixpanelDataService().getMixpanelEntityEventProperties(entityId);

    // then
    assertThat(mixpanelEntityEventProperties.getTime()).isLessThanOrEqualTo(System.currentTimeMillis());
    assertThat(mixpanelEntityEventProperties.getDistinctId()).isEmpty();
    assertThat(mixpanelEntityEventProperties.getInsertId()).isNotEmpty();
    assertThat(mixpanelEntityEventProperties.getProduct()).isEqualTo("optimize");
    assertThat(mixpanelEntityEventProperties.getStage()).isEqualTo(STAGE);
    assertThat(mixpanelEntityEventProperties.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
    assertThat(mixpanelEntityEventProperties.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(mixpanelEntityEventProperties.getEntityId()).isEqualTo(entityId);
  }

  private MixpanelConfiguration getMixpanelConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getAnalytics().getMixpanel();
  }

  @NonNull
  private MixpanelDataService getMixpanelDataService() {
    return embeddedOptimizeExtension.getApplicationContext().getBean(MixpanelDataService.class);
  }

  @SneakyThrows
  private void createManagementReport() {
    // The initial report is created for a specific process
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("procDefKey")
      .setProcessDefinitionVersion("1")
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(
      new SingleProcessReportDefinitionRequestDto(reportData));

    final UpdateRequest update = new UpdateRequest()
      .index(ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME)
      .id(reportId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.data.managementReport = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  protected void createManagementDashboard() {
    final String dashboardId = dashboardClient.createEmptyDashboard();

    final UpdateRequest update = new UpdateRequest()
      .index(DASHBOARD_INDEX_NAME)
      .id(dashboardId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.managementDashboard = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
