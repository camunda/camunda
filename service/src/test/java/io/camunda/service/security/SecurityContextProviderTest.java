/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityContextProviderTest {

  private SecurityContextProvider securityContextProvider;

  @BeforeEach
  void before() {
    securityContextProvider = new SecurityContextProvider();
  }

  @Test
  void shouldProvideSecurityContextWithAuthorizationWhenDisabled() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var authorization = mock(Authorization.class);

    // when
    final var securityContext =
        securityContextProvider.provideSecurityContext(authentication, authorization);

    // then
    assertThat(securityContext.authorization()).isEqualTo(authorization);
    assertThat(securityContext.authentication()).isEqualTo(authentication);
  }
}
