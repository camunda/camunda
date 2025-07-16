/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.authentication.NoSecondaryStorageUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.service.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.database.type=none" // no secondary storage
    })
@TestPropertySource(properties = {"camunda.database.type=none"})
public class NoSecondaryStorageWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @Test
  public void shouldUseNoSecondaryStorageUserDetailsService() {
    // when
    final UserDetailsService userDetailsService = webApplicationContext.getBean(UserDetailsService.class);

    // then
    assertThat(userDetailsService).isInstanceOf(NoSecondaryStorageUserDetailsService.class);
  }

  @Test
  public void shouldFailAuthenticationWithClearErrorMessage() {
    // given
    final UserDetailsService userDetailsService = webApplicationContext.getBean(UserDetailsService.class);

    // when & then
    final ServiceException exception = assertThrows(
        ServiceException.class,
        () -> userDetailsService.loadUserByUsername("demo"));

    assertThat(exception.getMessage())
        .contains("Authentication is not available when secondary storage is disabled")
        .contains("camunda.database.type=none");
        
    assertThat(exception.getStatus())
        .isEqualTo(ServiceException.Status.FORBIDDEN);
  }
}