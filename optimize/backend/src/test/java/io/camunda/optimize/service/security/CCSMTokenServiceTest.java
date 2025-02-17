/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CCSMTokenServiceTest {

  private static final String ACCESS_TOKEN_VALUE = "accessToken";
  private static final String OPTIMIZE_PERMISSION = "write:*";

  private static final String EMAIL = "user@example.com";
  private static final String ID = "user123";
  private static final String NAME = "name";
  private static final String USERNAME = "username";

  @Mock private AuthCookieService authCookieService;
  @Mock private ConfigurationService configurationService;
  @Mock private Identity identity;
  @Mock private Authentication authentication;
  @Mock private AccessToken accessToken;
  @Mock private UserDetails userDetails;

  private CCSMTokenService ccsmTokenService;

  @BeforeEach
  void setUp() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(ACCESS_TOKEN_VALUE)).thenReturn(accessToken);
    when(accessToken.getPermissions()).thenReturn(ImmutableList.of(OPTIMIZE_PERMISSION));

    ccsmTokenService = new CCSMTokenService(authCookieService, configurationService, identity);
  }

  @Test
  void getUserInfoFromTokenValidTokenReturnsUserDto() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.of(NAME));
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertEquals(ID, result.getId());
    assertEquals(NAME, result.getFirstName());
    assertEquals(EMAIL, result.getEmail());
    assertNull(result.getLastName());
    assertTrue(result.getRoles().isEmpty());
  }

  @Test
  void getUserInfoFromTokenMissingNameReturnsUsername() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.empty());
    when(userDetails.getUsername()).thenReturn(Optional.of(USERNAME));
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertEquals(ID, result.getId());
    assertEquals(USERNAME, result.getFirstName());
    assertEquals(EMAIL, result.getEmail());
    assertNull(result.getLastName());
    assertTrue(result.getRoles().isEmpty());
  }

  @Test
  void getUserInfoFromTokenMissingNameAndUsernameReturnsUserIdAsUsername() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.empty());
    when(userDetails.getUsername()).thenReturn(Optional.empty());
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertEquals(ID, result.getId());
    assertEquals(ID, result.getFirstName());
    assertEquals(EMAIL, result.getEmail());
    assertNull(result.getLastName());
    assertTrue(result.getRoles().isEmpty());
  }

  @Test
  void getUserInfoFromTokenMissingEmailReturnsUserIdAsEmail() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.of(NAME));
    when(userDetails.getEmail()).thenReturn(Optional.empty());

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertEquals(ID, result.getId());
    assertEquals(ID, result.getEmail());
    assertEquals(NAME, result.getFirstName());
    assertNull(result.getLastName());
    assertTrue(result.getRoles().isEmpty());
  }

  @Test
  void getUserInfoFromTokenInvalidTokenThrowsNotAuthorizedException() {
    when(accessToken.getPermissions()).thenReturn(ImmutableList.of());

    assertThrows(
        NotAuthorizedException.class,
        () -> ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE));
  }
}
