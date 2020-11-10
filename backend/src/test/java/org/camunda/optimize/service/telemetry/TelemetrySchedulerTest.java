/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry;

import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TelemetrySchedulerTest {

  @Mock
  private TelemetrySendingService telemetrySendingService;
  @Mock
  private TelemetryDataService telemetryDataService;
  @Mock
  private SettingsService settingsService;

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void initWithInvalidEndpointConfig_fails() {
    // given
    configurationService.getTelemetryConfiguration().setTelemetryEndpoint(null);

    // when
    final TelemetryScheduler underTest = createTelemetrySchedulerToTest();

    // then
    assertThrows(OptimizeConfigurationException.class, underTest::init);
  }

  @Test
  public void initWithInvalidReportingIntervalConfig_fails() {
    // given
    configurationService.getTelemetryConfiguration().setReportingIntervalInHours(-1);

    // when
    final TelemetryScheduler underTest = createTelemetrySchedulerToTest();

    // then
    assertThrows(OptimizeConfigurationException.class, underTest::init);
  }

  @Test
  public void telemetrySendingServiceIsCalled_whenEnabled() {
    // given
    final TelemetryScheduler underTest = createTelemetrySchedulerToTest();
    when(settingsService.getSettings()).thenReturn(getSettingsWithTelemetryEnabled());
    when(telemetryDataService.getTelemetryData()).thenReturn(getTestTelemetryData());
    doNothing().when(telemetrySendingService).sendTelemetryData(any(), any());

    // when
    underTest.run();

    // then
    verify(telemetrySendingService, times(1))
      .sendTelemetryData(
        getTestTelemetryData(),
        getConfiguredTelemetryEndpoint()
      );
  }

  @Test
  public void telemetrySendingServiceIsNotCalled_whenDisabled() {
    // given
    final TelemetryScheduler underTest = createTelemetrySchedulerToTest();
    when(settingsService.getSettings()).thenReturn(getSettingsWithTelemetryDisabled());

    // when
    underTest.run();

    // then
    verify(telemetrySendingService, never()).sendTelemetryData(any(), any());
  }

  private TelemetryScheduler createTelemetrySchedulerToTest() {
    return new TelemetryScheduler(
      telemetrySendingService,
      telemetryDataService,
      configurationService,
      settingsService
    );
  }

  private SettingsResponseDto getSettingsWithTelemetryEnabled() {
    return SettingsResponseDto.builder()
      .metadataTelemetryEnabled(true)
      .build();
  }

  private SettingsResponseDto getSettingsWithTelemetryDisabled() {
    return SettingsResponseDto.builder()
      .metadataTelemetryEnabled(false)
      .build();
  }

  private String getConfiguredTelemetryEndpoint() {
    return configurationService.getTelemetryConfiguration().getTelemetryEndpoint();
  }

  private TelemetryDataDto getTestTelemetryData() {
    final DatabaseDto databaseDto = DatabaseDto.builder().version("7.0.0").build();
    final InternalsDto internalsDto = InternalsDto.builder().database(databaseDto).build();
    final ProductDto productDto = ProductDto.builder().internals(internalsDto).build();

    return TelemetryDataDto.builder()
      .installation("1234-5678")
      .product(productDto)
      .build();
  }
}
