/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.NonNull;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelHeartbeatProperties;
import org.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MixpanelDataServiceIT extends AbstractIT {

  @Test
  public void heartbeatEventData() {
    // given
    final MixpanelConfiguration mixpanelConfiguration = getMixpanelConfiguration();
    final String organizationId = "orgId";
    final String stage = "IT";
    mixpanelConfiguration.getProperties().setStage(stage);
    mixpanelConfiguration.getProperties().setOrganizationId(organizationId);
    final String clusterId = "IT-cluster";
    mixpanelConfiguration.getProperties().setClusterId(clusterId);

    reportClient.createEmptySingleProcessReport();
    reportClient.createEmptySingleProcessReport();
    reportClient.createEmptySingleDecisionReport();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final MixpanelHeartbeatProperties mixpanelHeartbeatProperties =
      getMixpanelDataService().getMixpanelHeartbeatProperties();

    // then
    assertThat(mixpanelHeartbeatProperties.getTime()).isLessThanOrEqualTo(System.currentTimeMillis());
    assertThat(mixpanelHeartbeatProperties.getDistinctId()).isEmpty();
    assertThat(mixpanelHeartbeatProperties.getInsertId()).isNotEmpty();
    assertThat(mixpanelHeartbeatProperties.getProduct()).isEqualTo("optimize");
    assertThat(mixpanelHeartbeatProperties.getStage()).isEqualTo(stage);
    assertThat(mixpanelHeartbeatProperties.getOrganizationId()).isEqualTo(organizationId);
    assertThat(mixpanelHeartbeatProperties.getClusterId()).isEqualTo(clusterId);
    assertThat(mixpanelHeartbeatProperties.getProcessReportCount()).isEqualTo(2);
    assertThat(mixpanelHeartbeatProperties.getDecisionReportCount()).isEqualTo(1);
  }

  private MixpanelConfiguration getMixpanelConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getAnalytics().getMixpanel();
  }

  @NonNull
  private MixpanelDataService getMixpanelDataService() {
    return embeddedOptimizeExtension.getApplicationContext().getBean(MixpanelDataService.class);
  }
}
