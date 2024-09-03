/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.initializer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.configuration.DataInitializationConfiguration.InitDataProperties;
import io.camunda.service.UserServices;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.entities.UserEntity.User;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.client.protocol.rest.UserWithPasswordRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataInitializerTest {

  @Mock private UserServices<UserRecord> userServices;

  @Mock private InitDataProperties initDataProperties;

  @Mock private ApplicationReadyEvent applicationReadyEvent;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private SpringBrokerBridge brokerBridge;

  @Mock private BrokerHealthCheckService brokerHealthCheckService;

  private DataInitializer dataInitializer;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(userServices.createUser(any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord()));
    when(brokerBridge.getBrokerHealthCheckService())
        .thenReturn(Optional.of(brokerHealthCheckService));
    when(brokerHealthCheckService.isBrokerReady()).thenReturn(true);
    dataInitializer =
        new DataInitializer(userServices, passwordEncoder, brokerBridge, initDataProperties);
  }

  @Test
  void whenUsersNotExistsUserCreated() {
    when(initDataProperties.getUsers())
        .thenReturn(
            Arrays.asList(
                new UserWithPasswordRequest().username("username1").password("password"),
                new UserWithPasswordRequest().username("username2").password("password")));
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(initDataProperties).getUsers();
    verify(userServices, times(2)).findByUsername(any());
    verify(userServices).createUser(eq("username1"), eq("username1"), eq("username1"), any());
    verify(userServices).createUser(eq("username2"), eq("username2"), eq("username2"), any());
  }

  @Test
  void whenNoInitUsersNothingCreated() {
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(initDataProperties).getUsers();
    verifyNoInteractions(userServices);
  }

  @Test
  void whenInitUserAlreadyExistNothingCreated() {
    when(initDataProperties.getUsers())
        .thenReturn(
            Arrays.asList(
                new UserWithPasswordRequest().username("username1").password("password"),
                new UserWithPasswordRequest().username("username2").password("password")));
    when(userServices.findByUsername("username1"))
        .thenReturn(Optional.of(new UserEntity(new User("username1", "", "", ""))));
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(initDataProperties).getUsers();
    verify(userServices, times(2)).findByUsername(any());
    verify(userServices, never()).createUser(eq("username1"), any(), any(), any());
    verify(userServices).createUser(eq("username2"), any(), any(), any());
  }

  @Test
  void whenBrokerNotAvailableSkipAndNoExceptionThrown() {
    dataInitializer = new DataInitializer(userServices, passwordEncoder, null, initDataProperties);
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(initDataProperties, never()).getUsers();
  }

  @Test
  void whenBrokerNotReadyThenRetry() {
    when(brokerHealthCheckService.isBrokerReady())
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(true);
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(brokerHealthCheckService, times(3)).isBrokerReady();
  }
}
