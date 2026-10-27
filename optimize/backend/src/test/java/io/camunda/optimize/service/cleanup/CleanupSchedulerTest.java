/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CleanupSchedulerTest {

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void testAllEnabledCleanupServicesAreCalled() {
    // given
    final CleanupScheduler underTest = createOptimizeCleanupServiceToTest();
    final CleanupService mockedCleanupService1 = mock(CleanupService.class);
    final CleanupService mockedCleanupService2 = mock(CleanupService.class);
    final CleanupService mockedCleanupService3 = mock(CleanupService.class);
    when(mockedCleanupService1.isEnabled()).thenReturn(true);
    doNothing().when(mockedCleanupService1).doCleanup(any());
    when(mockedCleanupService2.isEnabled()).thenReturn(true);
    doNothing().when(mockedCleanupService2).doCleanup(any());
    when(mockedCleanupService3.isEnabled()).thenReturn(false);

    underTest.getCleanupServices().add(mockedCleanupService1);
    underTest.getCleanupServices().add(mockedCleanupService2);
    underTest.getCleanupServices().add(mockedCleanupService3);

    // when
    underTest.runCleanup();

    // then
    verify(mockedCleanupService1, times(1)).doCleanup(any());
    verify(mockedCleanupService2, times(1)).doCleanup(any());
    verify(mockedCleanupService3, never()).doCleanup(any());
  }

  @Test
  public void testFailingCleanupServiceDoesntAffectOthersExecution() {
    // given
    final CleanupScheduler underTest = createOptimizeCleanupServiceToTest();
    final CleanupService mockedCleanupService1 = mock(CleanupService.class);
    final CleanupService mockedCleanupService2 = mock(CleanupService.class);
    when(mockedCleanupService1.isEnabled()).thenReturn(true);
    doThrow(RuntimeException.class).when(mockedCleanupService1).doCleanup(any());
    when(mockedCleanupService2.isEnabled()).thenReturn(true);
    doNothing().when(mockedCleanupService2).doCleanup(any());

    underTest.getCleanupServices().add(mockedCleanupService1);
    underTest.getCleanupServices().add(mockedCleanupService2);

    // when
    underTest.runCleanup();

    // then
    verify(mockedCleanupService1, times(1)).doCleanup(any());
    verify(mockedCleanupService2, times(1)).doCleanup(any());
  }

  @Test
  public void testFailInitOnInvalidConfig() {
    // given
    getCleanupConfiguration().setTtl(null);

    // when
    final CleanupScheduler underTest = createOptimizeCleanupServiceToTest();

    // then
    assertThatExceptionOfType(OptimizeConfigurationException.class).isThrownBy(underTest::init);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private CleanupScheduler createOptimizeCleanupServiceToTest() {
    return new CleanupScheduler(configurationService, new ArrayList<>());
  }
}
