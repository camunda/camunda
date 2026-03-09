/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.entities.UserEntity;
import io.camunda.security.reader.ResourceAccessChecks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CamundaUserDetailsServiceTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserReader userReader;
  private CamundaUserDetailsService userDetailsService;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    userDetailsService = new CamundaUserDetailsService(userReader);
  }

  @Test
  void shouldLoadUserDetails() {
    when(userReader.getById(eq(TEST_USER_ID), any(ResourceAccessChecks.class)))
        .thenReturn(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1"));

    final var user = userDetailsService.loadUserByUsername(TEST_USER_ID);

    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(user.getPassword()).isEqualTo("password1");
  }

  @Test
  void shouldThrowWhenUserNotFound() {
    when(userReader.getById(eq(TEST_USER_ID), any(ResourceAccessChecks.class))).thenReturn(null);

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(TEST_USER_ID))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void shouldThrowWhenUsernameIsNull() {
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void shouldThrowWhenUsernameIsBlank() {
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("  "))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
