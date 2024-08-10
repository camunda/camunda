/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.tasklist.util.SpringContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityJwt2AuthenticationTokenConverterTest {
  @Mock private Identity identity;

  @Mock private AccessToken accessToken;

  @InjectMocks private SpringContextHolder springContextHolder;

  @Mock private ApplicationContext context;

  @InjectMocks private IdentityJwt2AuthenticationTokenConverter converter;

  @BeforeEach
  void setUp() {
    springContextHolder.setApplicationContext(context);
  }

  @Test
  public void convertValidJwtShouldReturnsJwtAuthenticationToken() {
    // given
    final Jwt jwt = mock(Jwt.class);

    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().verifyToken(jwt.getTokenValue())).thenReturn(accessToken);

    // when
    final AbstractAuthenticationToken result = converter.convert(jwt);

    // then
    assertTrue(result instanceof JwtAuthenticationToken);
    assertEquals(jwt, ((JwtAuthenticationToken) result).getToken());
  }

  @Test
  public void convertInvalidJwtShouldThrowsInsufficientAuthenticationException() {
    // given
    final Jwt jwt = mock(Jwt.class);

    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().verifyToken(jwt.getTokenValue()))
        .thenThrow(new RuntimeException("Invalid JWT"));

    // then
    assertThrows(InsufficientAuthenticationException.class, () -> converter.convert(jwt));
  }

  @Test
  public void convertJwtWithInvalidAudienceShouldThrowsInsufficientAuthenticationException() {
    // given
    final Jwt jwt = mock(Jwt.class);

    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().verifyToken(jwt.getTokenValue()))
        .thenThrow(new RuntimeException("Invalid audience"));

    // then
    assertThrows(InsufficientAuthenticationException.class, () -> converter.convert(jwt));
  }

  @Test
  public void convertNullJwtThrowsIllegalArgumentException() {
    // given
    final Jwt jwt = null;

    // then
    assertThrows(InsufficientAuthenticationException.class, () -> converter.convert(jwt));
  }
}
