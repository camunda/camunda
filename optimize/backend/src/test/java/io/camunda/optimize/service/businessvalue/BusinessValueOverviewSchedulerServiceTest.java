/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessValueOverviewSchedulerServiceTest {

  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewSchedulerService scheduler;

  @BeforeEach
  void setUp() {
    computeService = mock(BusinessValueOverviewComputeService.class);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    final BusinessValueConfiguration businessValueConfiguration = new BusinessValueConfiguration();
    businessValueConfiguration.setOverviewRefreshInterval(86_400L);
    when(configurationService.getBusinessValueConfiguration())
        .thenReturn(businessValueConfiguration);
    scheduler = new BusinessValueOverviewSchedulerService(computeService, configurationService);
  }

  @Test
  void shouldTickAcrossAllRanges() {
    // when a tick fires
    scheduler.runOverviewComputeTask();

    // then compute is invoked once with all 4 range presets
    verify(computeService)
        .computeOverviewRows(eq(BusinessValueOverviewSchedulerService.ALL_RANGES));
  }
}
