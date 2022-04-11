/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.NonNull;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEntityEventProperties;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelHeartbeatProperties;
import org.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    final String collectionId = collectionClient.createNewCollection();
    final String reportInCollectionId = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    reportClient.createEmptySingleDecisionReport();
    dashboardClient.createEmptyDashboard();
    alertClient.createAlertForReport(reportInCollectionId);

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
    assertThat(mixpanelHeartbeatProperties.getProcessReportCount()).isEqualTo(2);
    assertThat(mixpanelHeartbeatProperties.getDecisionReportCount()).isEqualTo(1);
    assertThat(mixpanelHeartbeatProperties.getDashboardCount()).isEqualTo(1);
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
}
