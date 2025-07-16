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

import io.camunda.service.exception.ServiceException;
import org.junit.jupiter.api.Test;

public class NoSecondaryStorageUserDetailsServiceTest {

  @Test
  public void shouldThrowServiceExceptionWithClearMessage() {
    // given
    final NoSecondaryStorageUserDetailsService userDetailsService = new NoSecondaryStorageUserDetailsService();

    // when & then
    final ServiceException exception = assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername("demo"));

    assertThat(exception.getMessage())
        .contains("Authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none")
        .contains("Please configure secondary storage to enable authentication");
        
    assertThat(exception.getStatus())
        .isEqualTo(ServiceException.Status.FORBIDDEN);
  }

  @Test
  public void shouldThrowServiceExceptionForAnyUsername() {
    // given
    final NoSecondaryStorageUserDetailsService userDetailsService = new NoSecondaryStorageUserDetailsService();

    // when & then
    assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername("admin"));

    assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername("user"));

    assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername(""));

    assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername(null));
  }
}