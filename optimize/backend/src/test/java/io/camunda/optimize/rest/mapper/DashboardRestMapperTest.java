/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import org.junit.jupiter.api.Test;

public class DashboardRestMapperTest {

  private static final String LOCALE = "en";

  private final AbstractIdentityService identityService = mock(AbstractIdentityService.class);
  private final LocalizationService localizationService = mock(LocalizationService.class);

  private final DashboardRestMapper underTest =
      new DashboardRestMapper(identityService, localizationService);

  @Test
  void shouldLocalizeAgenticControlDashboardNameViaAgenticControlCategory() {
    // given an agentic control dashboard whose name is a localization code
    when(localizationService.validateAndReturnValidLocale(LOCALE)).thenReturn(LOCALE);
    when(localizationService.getLocalizationForAgenticControlDashboardCode(
            LOCALE, "agenticControlPlaneDashboardName"))
        .thenReturn("Agentic Control Dashboard");

    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setAgenticControlDashboard(true);
    dashboard.setName("agenticControlPlaneDashboardName");

    // when
    underTest.prepareRestResponse(dashboard, LOCALE);

    // then the name is resolved via the agentic control category, not the management one
    assertThat(dashboard.getName()).isEqualTo("Agentic Control Dashboard");
    verify(localizationService, never()).getLocalizationForManagementDashboardCode(any(), any());
  }
}
