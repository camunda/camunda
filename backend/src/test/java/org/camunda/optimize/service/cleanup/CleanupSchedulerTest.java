/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    assertThrows(OptimizeConfigurationException.class, underTest::init);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private CleanupScheduler createOptimizeCleanupServiceToTest() {
    return new CleanupScheduler(configurationService, new ArrayList<>());
  }
}
