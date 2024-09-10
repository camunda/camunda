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
import static org.mockito.Mockito.when;

import io.camunda.service.UserServices;
import io.camunda.service.entities.UserEntity;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsServiceTest {

  private static final long TEST_USER_ID = 1;
  private static final String TEST_USER_NAME = "username1";

  @Mock private UserServices userService;
  private CamundaUserDetailsService userDetailsService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    userDetailsService = new CamundaUserDetailsService(userService);
  }

  @Test
  public void testUserDetailsIsLoaded() {
    // given
    when(userService.findByUsername(TEST_USER_NAME))
        .thenReturn(Optional.of(new UserEntity(TEST_USER_ID, TEST_USER_NAME, "", "", "password1")));
    // when
    final UserDetails user = userDetailsService.loadUserByUsername(TEST_USER_NAME);

    // then
    assertThat(user.getUsername()).isEqualTo(TEST_USER_NAME);
    assertThat(user.getPassword()).isEqualTo("password1");
  }

  @Test
  public void testUserDetailsNotFound() {
    // given
    when(userService.findByUsername(TEST_USER_NAME)).thenReturn(Optional.empty());
    // when/then
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(TEST_USER_NAME))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
