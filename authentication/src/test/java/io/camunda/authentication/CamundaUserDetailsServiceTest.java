/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsServiceTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserServices userService;
  private CamundaUserDetailsService userDetailsService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    userDetailsService = new CamundaUserDetailsService(userService);
    when(userService.withAuthentication(any(CamundaAuthentication.class))).thenReturn(userService);
  }

  @Test
  public void testUserDetailsIsLoaded() {
    // given
    when(userService.getUser(any()))
        .thenReturn(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1"));

    // when
    final var user = userDetailsService.loadUserByUsername(TEST_USER_ID);

    // then
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(user.getPassword()).isEqualTo("password1");
  }

  @Test
  public void testUserDetailsNotFound() {
    // given
    when(userService.getUser(any())).thenThrow(new ServiceException("not found", Status.NOT_FOUND));

    // when/then
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(TEST_USER_ID))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
