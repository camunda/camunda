/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class AuthorizationCheckerTest {

  @Mock private AuthorizationReader authorizationReader;
  private AuthorizationChecker authorizationChecker;

  @BeforeEach
  public void setUp() {
    authorizationChecker = new AuthorizationChecker(authorizationReader);
  }

  @Test
  public void noResourceIdsReturnedWhenOwnerIdsIsEmpty() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var authorization = mock(Authorization.class);
    final var securityContext = mock(SecurityContext.class);
    when(securityContext.authentication()).thenReturn(authentication);
    when(securityContext.authorization()).thenReturn(authorization);

    // when
    final var result = authorizationChecker.retrieveAuthorizedResourceIds(securityContext);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void noPermissionTypesReturnedWhenOwnerIdsIsEmpty() {
    // given
    final var authentication = mock(CamundaAuthentication.class);

    // when
    final var result =
        authorizationChecker.collectPermissionTypes("foo", APPLICATION, authentication);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void notAuthorizedWhenOwnerIdsIsEmpty() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var authorization = mock(Authorization.class);
    final var securityContext = mock(SecurityContext.class);
    when(securityContext.authentication()).thenReturn(authentication);
    when(securityContext.authorization()).thenReturn(authorization);

    // when
    final var result = authorizationChecker.isAuthorized("foo", securityContext);

    // then
    assertThat(result).isFalse();
  }
}
