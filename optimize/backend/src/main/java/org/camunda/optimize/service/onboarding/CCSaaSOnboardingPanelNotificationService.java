/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboarding;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.DashboardRestService.DASHBOARD_PATH;
import static org.camunda.optimize.rest.DashboardRestService.INSTANT_PREVIEW_PATH;
import static org.camunda.optimize.service.util.PanelNotificationConstants.INITIAL_VISIT_TO_INSTANT_DASHBOARD_CONTENT;
import static org.camunda.optimize.service.util.PanelNotificationConstants.INITIAL_VISIT_TO_INSTANT_DASHBOARD_ID;
import static org.camunda.optimize.service.util.PanelNotificationConstants.INITIAL_VISIT_TO_INSTANT_DASHBOARD_LINK_LABEL;
import static org.camunda.optimize.service.util.PanelNotificationConstants.INITIAL_VISIT_TO_INSTANT_DASHBOARD_TITLE;
import static org.camunda.optimize.service.util.PanelNotificationConstants.OPTIMIZE_SOURCE;
import static org.camunda.optimize.service.util.PanelNotificationConstants.OPTIMIZE_USER_PERMISSIONS;
import static org.camunda.optimize.service.util.PanelNotificationConstants.ORG_TYPE;

import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationDataDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationMetaDataDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationRequestDto;
import org.camunda.optimize.rest.cloud.CCSaaSNotificationClient;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.util.RootUrlGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.elasticsearch.core.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSOnboardingPanelNotificationService {

  public static final String INSTANT_DASHBOARD_LINK_TEMPLATE =
      "%s" + DASHBOARD_PATH + INSTANT_PREVIEW_PATH + "/%s";
  private final CCSaaSNotificationClient notificationClient;
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final RootUrlGenerator rootUrlGenerator;

  public void sendOnboardingPanelNotification(final String processKey) {
    notificationClient.sendPanelNotificationToOrg(
        PanelNotificationRequestDto.builder()
            .notification(
                createPanelNotificationData(
                    createUniqueNotificationId(processKey),
                    configurationService
                        .getAuthConfiguration()
                        .getCloudAuthConfiguration()
                        .getOrganizationId(),
                    processKey,
                    createPanelNotificationContent(getProcessName(processKey))))
            .build());
  }

  private PanelNotificationDataDto createPanelNotificationData(
      final String uniqueId,
      final String orgId,
      final String processKey,
      final String notificationContent) {
    return PanelNotificationDataDto.builder()
        .uniqueId(uniqueId) // to ensure no duplicate notifications per process
        .source(OPTIMIZE_SOURCE)
        .type(ORG_TYPE)
        .orgId(orgId)
        .title(INITIAL_VISIT_TO_INSTANT_DASHBOARD_TITLE)
        .description(notificationContent)
        .meta(
            PanelNotificationMetaDataDto.builder()
                .permissions(OPTIMIZE_USER_PERMISSIONS)
                .identifier(INITIAL_VISIT_TO_INSTANT_DASHBOARD_ID)
                .href(createInstantPreviewDashboardLink(processKey))
                .label(INITIAL_VISIT_TO_INSTANT_DASHBOARD_LINK_LABEL)
                .build())
        .build();
  }

  private String createPanelNotificationContent(final String processName) {
    return String.format(INITIAL_VISIT_TO_INSTANT_DASHBOARD_CONTENT, processName);
  }

  private String createInstantPreviewDashboardLink(final String processKey) {
    return String.format(INSTANT_DASHBOARD_LINK_TEMPLATE, generateRootDashboardLink(), processKey);
  }

  private String createUniqueNotificationId(final String processKey) {
    return String.format("%s_%s", INITIAL_VISIT_TO_INSTANT_DASHBOARD_ID, processKey);
  }

  public String generateRootDashboardLink() {
    String rootUrl = rootUrlGenerator.getRootUrl();
    return String.format("%s/#", rootUrl);
  }

  private String getProcessName(final String processKey) {
    return definitionService
        .getDefinition(
            DefinitionType.PROCESS,
            processKey,
            List.of(ALL_VERSIONS),
            Collections.emptyList() // TODO to be adjusted with OPT-7113
            )
        .map(DefinitionOptimizeResponseDto::getName)
        .orElse(processKey);
  }
}
