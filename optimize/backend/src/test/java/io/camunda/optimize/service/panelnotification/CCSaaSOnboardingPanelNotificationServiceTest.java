/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.panelnotification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationDataDto;
import io.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationMetaDataDto;
import io.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationRequestDto;
import io.camunda.optimize.rest.cloud.CCSaaSNotificationClient;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.onboarding.CCSaaSOnboardingPanelNotificationService;
import io.camunda.optimize.service.util.RootUrlGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CCSaaSOnboardingPanelNotificationServiceTest {

  private static final String PROCESS_KEY = "aProcessKey";
  private static final String PROCESS_NAME = "aProcessName";
  private static final String ORG_ID = "anOrgId";
  private static final String CLUSTER_ID = "aClusterId";
  @Mock private CCSaaSNotificationClient notificationClient;
  @Mock private DefinitionService definitionService;
  private CCSaaSOnboardingPanelNotificationService underTest;
  @Mock private RootUrlGenerator rootUrlGenerator;

  @BeforeEach
  public void setup() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setOrganizationId(ORG_ID);
    underTest =
        new CCSaaSOnboardingPanelNotificationService(
            notificationClient, configurationService, definitionService, rootUrlGenerator);
  }

  @Test
  public void panelNotificationDataIsCreatedCorrectly() {
    // given
    final DefinitionOptimizeResponseDto returnedDefWithName = new ProcessDefinitionOptimizeDto();
    returnedDefWithName.setName(PROCESS_NAME);
    when(definitionService.getDefinition(any(), any(), any(), any()))
        .thenReturn(Optional.of(returnedDefWithName));
    when(rootUrlGenerator.getRootUrl()).thenReturn("http://localhost:8090/" + CLUSTER_ID);

    // when
    final ArgumentCaptor<PanelNotificationRequestDto> actualNotification =
        ArgumentCaptor.forClass(PanelNotificationRequestDto.class);
    underTest.sendOnboardingPanelNotification(PROCESS_KEY);
    verify(notificationClient).sendPanelNotificationToOrg(actualNotification.capture());

    // then
    assertThat(actualNotification.getValue().getNotification())
        .isEqualTo(createExpectedNotificationDataDto());
  }

  private PanelNotificationDataDto createExpectedNotificationDataDto() {
    return PanelNotificationDataDto.builder()
        .uniqueId("initialVisitToInstantDashboard_" + PROCESS_KEY)
        .source("optimize")
        .type("org")
        .orgId(ORG_ID)
        .title("See how your process is doing")
        .description(
            "Your first process of "
                + PROCESS_NAME
                + " was started successfully. Track the status in the instant "
                + "preview dashboard.")
        .meta(createExpectedPanelNotificationMetadataDto())
        .build();
  }

  private PanelNotificationMetaDataDto createExpectedPanelNotificationMetadataDto() {
    return PanelNotificationMetaDataDto.builder()
        .permissions(new String[] {"cluster:optimize:read"})
        .identifier("initialVisitToInstantDashboard")
        .href(
            String.format(
                "http://localhost:8090/%s/#/dashboard/instant/%s", CLUSTER_ID, PROCESS_KEY))
        .label("View instant preview dashboard")
        .build();
  }
}
