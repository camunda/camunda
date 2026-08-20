/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.service.db.reader.AlertReader;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.reader.SharingReader;
import io.camunda.optimize.service.mixpanel.client.MixpanelEntityEventProperties;
import io.camunda.optimize.service.mixpanel.client.MixpanelHeartbeatMetrics;
import io.camunda.optimize.service.mixpanel.client.MixpanelHeartbeatProperties;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.springframework.stereotype.Component;

@Component
public class MixpanelDataService {

  private final ConfigurationService configurationService;
  private final ReportReader reportReader;
  private final DashboardReader dashboardReader;
  private final AlertReader alertReader;
  private final SharingReader sharingReader;

  public MixpanelDataService(
      final ConfigurationService configurationService,
      final ReportReader reportReader,
      final DashboardReader dashboardReader,
      final AlertReader alertReader,
      final SharingReader sharingReader) {
    this.configurationService = configurationService;
    this.reportReader = reportReader;
    this.dashboardReader = dashboardReader;
    this.alertReader = alertReader;
    this.sharingReader = sharingReader;
  }

  public MixpanelHeartbeatProperties getMixpanelHeartbeatProperties() {
    final MixpanelConfiguration.TrackingProperties mixpanelProperties = getMixpanelProperties();
    return new MixpanelHeartbeatProperties(
        new MixpanelHeartbeatMetrics(
            reportReader.getReportCount(ReportType.PROCESS),
            reportReader.getReportCount(ReportType.DECISION),
            dashboardReader.getDashboardCount(),
            sharingReader.getReportShareCount(),
            sharingReader.getDashboardShareCount(),
            alertReader.getAlertCount(),
            reportReader.getUserTaskReportCount()),
        mixpanelProperties.getStage(),
        mixpanelProperties.getOrganizationId(),
        mixpanelProperties.getClusterId());
  }

  public MixpanelEntityEventProperties getMixpanelEntityEventProperties(final String entityId) {
    final MixpanelConfiguration.TrackingProperties mixpanelProperties = getMixpanelProperties();
    return new MixpanelEntityEventProperties(
        entityId,
        mixpanelProperties.getStage(),
        mixpanelProperties.getOrganizationId(),
        mixpanelProperties.getClusterId());
  }

  private MixpanelConfiguration.TrackingProperties getMixpanelProperties() {
    return configurationService.getAnalytics().getMixpanel().getProperties();
  }
}
