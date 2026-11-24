/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.rest.mapper.DashboardRestMapper;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.AgenticPhase2Configuration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardRestServiceAgentInsightsTest {

  private final DashboardService dashboardService = mock(DashboardService.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);
  private final AgenticPhase2Configuration phase2Configuration = new AgenticPhase2Configuration();
  private final HttpServletRequest request = mock(HttpServletRequest.class);

  private final DashboardRestService underTest =
      new DashboardRestService(
          dashboardService,
          mock(InstantPreviewDashboardService.class),
          mock(SessionService.class),
          mock(DashboardRestMapper.class),
          new AgenticPhase2Guard(configurationService));

  @BeforeEach
  void setUp() {
    when(configurationService.getAgenticPhase2Configuration()).thenReturn(phase2Configuration);
  }

  @Test
  void shouldReturnNotFoundWhenPhase2IsDisabled() {
    // given
    phase2Configuration.setEnabled(false);

    // when / then
    assertThatThrownBy(() -> underTest.getAgentInsightsDashboard(request))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(dashboardService);
  }

  @Test
  void shouldReturnDashboardWhenPhase2IsEnabled() {
    // given
    phase2Configuration.setEnabled(true);
    final AuthorizedDashboardDefinitionResponseDto dashboard =
        new AuthorizedDashboardDefinitionResponseDto(
            RoleType.VIEWER, new DashboardDefinitionRestDto());
    when(dashboardService.getAgentInsightsDashboard()).thenReturn(dashboard);

    // when
    final AuthorizedDashboardDefinitionResponseDto response =
        underTest.getAgentInsightsDashboard(request);

    // then
    assertThat(response).isSameAs(dashboard);
  }
}
