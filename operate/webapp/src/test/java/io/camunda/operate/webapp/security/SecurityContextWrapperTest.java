/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextWrapperTest {

  private SecurityContextWrapper securityContextWrapperComponent;

  @BeforeEach
  public void setUp() {
    securityContextWrapperComponent = new SecurityContextWrapper();
  }

  @Test
  public void testGetAuthentication() {
    final Authentication authentication = Mockito.mock(Authentication.class);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);
    final Authentication res = securityContextWrapperComponent.getAuthentication();

    assertThat(res).isSameAs(authentication);
  }
}
