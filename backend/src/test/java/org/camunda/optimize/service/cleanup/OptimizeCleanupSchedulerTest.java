/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class OptimizeCleanupSchedulerTest {

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void testAllCleanupServicesAreCalled() {
    // given
    final OptimizeCleanupScheduler underTest = createOptimizeCleanupServiceToTest();
    final OptimizeCleanupService mockedCleanupService1 = mock(OptimizeCleanupService.class);
    final OptimizeCleanupService mockedCleanupService2 = mock(OptimizeCleanupService.class);
    doNothing().when(mockedCleanupService1).doCleanup(any());
    doNothing().when(mockedCleanupService2).doCleanup(any());

    underTest.getCleanupServices().add(mockedCleanupService1);
    underTest.getCleanupServices().add(mockedCleanupService2);

    //when
    underTest.runCleanup();

    //then
    verify(mockedCleanupService1, times(1)).doCleanup(any());
    verify(mockedCleanupService2, times(1)).doCleanup(any());
  }

  @Test
  public void testFailingCleanupServiceDoesntAffectOthersExecution() {
    // given
    final OptimizeCleanupScheduler underTest = createOptimizeCleanupServiceToTest();
    final OptimizeCleanupService mockedCleanupService1 = mock(OptimizeCleanupService.class);
    final OptimizeCleanupService mockedCleanupService2 = mock(OptimizeCleanupService.class);
    doThrow(RuntimeException.class).when(mockedCleanupService1).doCleanup(any());
    doNothing().when(mockedCleanupService2).doCleanup(any());

    underTest.getCleanupServices().add(mockedCleanupService1);
    underTest.getCleanupServices().add(mockedCleanupService2);

    //when
    underTest.runCleanup();

    //then
    verify(mockedCleanupService1, times(1)).doCleanup(any());
    verify(mockedCleanupService2, times(1)).doCleanup(any());
  }

  @Test
  public void testFailInitOnInvalidConfig() {
    // given
    getCleanupConfiguration().setDefaultTtl(null);

    //when
    final OptimizeCleanupScheduler underTest = createOptimizeCleanupServiceToTest();

    //then
    assertThrows(OptimizeConfigurationException.class, underTest::init);
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private OptimizeCleanupScheduler createOptimizeCleanupServiceToTest() {
    return new OptimizeCleanupScheduler(configurationService, new ArrayList<>());
  }
}
