/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityUserReaderTest {

  static final List<String> GROUPS = List.of("Group A", "Group B");
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
  public void shouldReturnTheUserIdPermissionsAndGroupsByIdentityAuth() {
    // given
    final IdentityAuthentication identityAuthentication = mock(IdentityAuthentication.class);

    when(identityAuthentication.getId()).thenReturn("user123");
    when(identityAuthentication.getName()).thenReturn("userIdTest");
    when(identityAuthentication.getPermissions()).thenReturn(List.of(Permission.WRITE));
    when(identityAuthentication.getGroups()).thenReturn(GROUPS);

    // when
    final Optional<UserDTO> currentUser =
        identityUserReader.getCurrentUserBy(identityAuthentication);

    // then
    assertTrue(currentUser.isPresent());
    assertEquals("userIdTest", currentUser.get().getUserId());
    assertEquals(List.of(Permission.WRITE), currentUser.get().getPermissions());
    assertEquals(GROUPS, currentUser.get().getGroups());
  }

  @Test
  public void shouldReturnTheUserIdAndPermissionsAndGroupsJwtAuthenticationToken() {
    // given
    final Jwt jwt = mock(Jwt.class);
    final var jwtAuthenticationToken = mock(IdentityTenantAwareJwtAuthenticationToken.class);
    when(jwtAuthenticationToken.getName()).thenReturn("demo");
    when(jwtAuthenticationToken.getTenants()).thenReturn(Collections.emptyList());
    when(jwtAuthenticationToken.getPrincipal()).thenReturn(jwt);

    when(identity.authentication())
        .thenReturn(mock(io.camunda.identity.sdk.authentication.Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));

    // when
    final Optional<UserDTO> result = identityUserReader.getCurrentUserBy(jwtAuthenticationToken);

    // then
    assertThat(result)
        .isPresent()
        .contains(
            new UserDTO()
                .setUserId("demo")
                .setDisplayName("demo")
                .setPermissions(Collections.emptyList())
                .setC8Links(Collections.emptyList())
                .setTenants(Collections.emptyList()));
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

  @Test
  public void shouldReturnExceptionWhenGettingToken() {
    final Jwt jwt = mock(Jwt.class);
    final var jwtAuthenticationToken = mock(IdentityTenantAwareJwtAuthenticationToken.class);

    assertThatThrownBy(() -> identityUserReader.getUserToken(jwtAuthenticationToken))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Get token is not supported for Identity authentication");
  }
}
