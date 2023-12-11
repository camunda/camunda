/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.panelnotification;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationDataDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationMetaDataDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationRequestDto;
import org.camunda.optimize.rest.cloud.CCSaaSNotificationClient;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.onboarding.CCSaaSOnboardingPanelNotificationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CCSaaSOnboardingPanelNotificationServiceTest {

  private static final String PROCESS_KEY = "aProcessKey";
  private static final String PROCESS_NAME = "aProcessName";
  private static final String ORG_ID = "anOrgId";
  private static final String CLUSTER_ID = "aClusterId";
  @Mock
  private CCSaaSNotificationClient notificationClient;
  @Mock
  private DefinitionService definitionService;
  private CCSaaSOnboardingPanelNotificationService underTest;

  @BeforeEach
  public void setup() {
    final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getAuthConfiguration().getCloudAuthConfiguration().setClusterId(CLUSTER_ID);
    configurationService.getAuthConfiguration().getCloudAuthConfiguration().setOrganizationId(ORG_ID);
    underTest = new CCSaaSOnboardingPanelNotificationService(
      notificationClient,
      configurationService,
      definitionService
    );
  }

  @Test
  public void panelNotificationDataIsCreatedCorrectly() {
    // given
    final DefinitionOptimizeResponseDto returnedDefWithName = new ProcessDefinitionOptimizeDto();
    returnedDefWithName.setName(PROCESS_NAME);
    when(definitionService.getDefinition(any(), any(), any(), any())).thenReturn(Optional.of(returnedDefWithName));

    // when
    ArgumentCaptor<PanelNotificationRequestDto> actualNotification = ArgumentCaptor.forClass(PanelNotificationRequestDto.class);
    underTest.sendOnboardingPanelNotification(PROCESS_KEY);
    verify(notificationClient).sendPanelNotificationToOrg(actualNotification.capture());

    // then
    assertThat(actualNotification.getValue().getNotification()).isEqualTo(createExpectedNotificationDataDto());
  }

  private PanelNotificationDataDto createExpectedNotificationDataDto() {
    return PanelNotificationDataDto.builder()
      .uniqueId("initialVisitToInstantDashboard_" + PROCESS_KEY)
      .source("optimize")
      .type("org")
      .orgId(ORG_ID)
      .title("See how your process is doing")
      .description("Your first process of " + PROCESS_NAME + " was started successfully. Track the status in the instant " +
                     "preview dashboard.")
      .meta(createExpectedPanelNotificationMetadataDto())
      .build();
  }

  private PanelNotificationMetaDataDto createExpectedPanelNotificationMetadataDto() {
    return PanelNotificationMetaDataDto.builder()
      .permissions(new String[]{"cluster:optimize:read"})
      .identifier("initialVisitToInstantDashboard")
      .href(String.format(
        "http://localhost:8090/%s/#/dashboard/instant/%s",
        CLUSTER_ID,
        PROCESS_KEY
      ))
      .label("View instant preview dashboard")
      .build();
  }

}
