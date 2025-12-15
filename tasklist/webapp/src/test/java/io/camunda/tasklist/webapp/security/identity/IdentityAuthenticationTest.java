/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.Permission;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityAuthenticationTest {
  @Mock private Tokens tokens;
  @Mock private DecodedJWT decodedJWT;
  @Mock private AccessToken accessToken;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private Identity identity;
  @InjectMocks private IdentityAuthentication identityAuthentication;
  @Mock private ApplicationContext context;
  @Mock private Object details;
  private final long futureTime = System.currentTimeMillis() + 100000L;
  private final long pastTime = System.currentTimeMillis() - 100000L;

  @BeforeEach
  void setUp() {
    springContextHolder.setApplicationContext(context);
  }

  @Test
  public void getCredentialsShouldReturnAccessToken() {
    // given
    when(tokens.getAccessToken()).thenReturn("access_token");

    // when
    final String credentials = identityAuthentication.getCredentials();

    // then
    assertEquals("access_token", credentials);
  }

  @Test
  public void authenticatedShouldReturnTrueWhenAccessTokenNotExpired() {
    // when
    identityAuthentication.setAuthenticated(true);
    identityAuthentication.setExpires(new Date(futureTime));

    // then
    assertTrue(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticateShouldReturnFalseWhenRefreshTokenIsExpired() {
    // given
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnTrueWhenAccessTokenExpiredButRefreshTokenValid() {
    // given
    identityAuthentication.setAuthenticated(true);

    final List<String> permissionsList =
        Arrays.asList(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE);
    identityAuthentication.setPermissions(permissionsList);
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));
    when(accessToken.getPermissions()).thenReturn(permissionsList);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getToken().getExpiresAt()).thenReturn(new Date(futureTime));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().decodeJWT(any()).getExpiresAt())
        .thenReturn(new Date(futureTime));
    // then
    assertTrue(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnFalseWhenAccessTokenExpiredButRefreshTokenInvalid() {
    // given
    identityAuthentication.setAuthenticated(true);
    final List<String> permissionsList =
        Arrays.asList(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE);
    identityAuthentication.setPermissions(permissionsList);

    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().decodeJWT(any()).getExpiresAt())
        .thenReturn(new Date(futureTime));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));
    when(accessToken.getPermissions()).thenReturn(permissionsList);
    when(accessToken.getToken().getExpiresAt()).thenReturn(new Date(pastTime));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnFalseWhenNoPermissions() {
    // given
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void getPermissionsShouldReturnListOfPermissions() {
    // given
    final List<Permission> expectedPermissions = Arrays.asList(Permission.READ, Permission.WRITE);
    identityAuthentication.setPermissions(
        List.of(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE));

    // then
    assertEquals(expectedPermissions, identityAuthentication.getPermissions());
  }
}
