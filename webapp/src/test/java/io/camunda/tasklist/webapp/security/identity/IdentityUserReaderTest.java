/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityUserReaderTest {
  @Mock private Identity identity;
  @Mock private ApplicationContext context;
  @Mock private AccessToken accessToken;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private DecodedJWT decodedJWT;
  @InjectMocks private IdentityUserReader identityUserReader;

  @BeforeEach
  void setUp() {
    springContextHolder.setApplicationContext(context);
  }

  @Test
  public void shouldReturnTheUserIdAndPermissionsByIdentityAuth() {
    // given
    final IdentityAuthentication identityAuthentication = mock(IdentityAuthentication.class);

    when(identityAuthentication.getId()).thenReturn("user123");
    when(identityAuthentication.getName()).thenReturn("userIdTest");
    when(identityAuthentication.getPermissions()).thenReturn(List.of(Permission.WRITE));

    // when
    final Optional<UserDTO> currentUser =
        identityUserReader.getCurrentUserBy(identityAuthentication);

    // then
    assertTrue(currentUser.isPresent());
    assertEquals("userIdTest", currentUser.get().getUserId());
    assertEquals(List.of(Permission.WRITE), currentUser.get().getPermissions());
  }

  @Test
  public void shouldReturnTheUserIdAndPermissionsByJwtAuthenticationToken() {
    // given
    final Jwt jwt = mock(Jwt.class);
    when(jwt.getTokenValue()).thenReturn("jwtToken");
    final JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(jwt);

    when(identity.authentication())
        .thenReturn(mock(io.camunda.identity.sdk.authentication.Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));

    // when
    final Optional<UserDTO> currentUser =
        identityUserReader.getCurrentUserBy(jwtAuthenticationToken);

    // then
    assertTrue(currentUser.isPresent());
  }

  @Test
  public void shouldReturnEmptyForOtherAuthentication() {
    // given
    final Authentication authentication =
        new UsernamePasswordAuthenticationToken("user123", "password");

    // when
    final Optional<UserDTO> currentUser = identityUserReader.getCurrentUserBy(authentication);

    // then
    assertFalse(currentUser.isPresent());
  }
}
