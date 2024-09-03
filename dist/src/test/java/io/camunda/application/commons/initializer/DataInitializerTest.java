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
import io.camunda.zeebe.client.protocol.rest.UserWithPasswordRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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

  private DataInitializer dataInitializer;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    dataInitializer = new DataInitializer(userServices, passwordEncoder, initDataProperties);
  }

  @AfterEach
  void tearDown() {}

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
  }

  @Test
  void whenBrokerNotAvailableSkipAndNoExceptionThrown() {
    when(initDataProperties.getUsers())
        .thenReturn(
            Arrays.asList(
                new UserWithPasswordRequest().username("username1").password("password"),
                new UserWithPasswordRequest().username("username2").password("password")));
    when(userServices.findByUsername("username1")).thenThrow(new RuntimeException(""));
    dataInitializer.onApplicationEvent(applicationReadyEvent);
    verify(initDataProperties).getUsers();
    verify(userServices, times(1)).findByUsername(any());
  }
}
