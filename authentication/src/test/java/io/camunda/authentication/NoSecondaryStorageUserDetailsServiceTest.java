/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class NoSecondaryStorageUserDetailsServiceTest {

  @Test
  public void shouldThrowUsernameNotFoundExceptionWithClearMessage() {
    // given
    final NoSecondaryStorageUserDetailsService userDetailsService = new NoSecondaryStorageUserDetailsService();

    // when & then
    final UsernameNotFoundException exception = assertThrows(
        UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername("demo"));

    assertThat(exception.getMessage())
        .contains("Authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none")
        .contains("Please configure secondary storage to enable authentication");
  }

  @Test
  public void shouldThrowUsernameNotFoundExceptionForAnyUsername() {
    // given
    final NoSecondaryStorageUserDetailsService userDetailsService = new NoSecondaryStorageUserDetailsService();

    // when & then
    assertThrows(
        UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername("admin"));

    assertThrows(
        UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername("user"));

    assertThrows(
        UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername(""));

    assertThrows(
        UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername(null));
  }
}