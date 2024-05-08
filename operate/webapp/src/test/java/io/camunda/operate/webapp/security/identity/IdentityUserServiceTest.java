/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.TokenAuthentication;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
public class IdentityUserServiceTest {

  private IdentityUserService underTest;

  @Mock private Identity mockIdentity;

  @Mock private PermissionConverter mockPermissionConverter;

  @BeforeEach
  public void setup() {
    underTest = new IdentityUserService(mockIdentity, mockPermissionConverter);
  }

  @Test
  public void testCreateUserDtoFromIdentityAuthentication() {
    final var identityAuthentication = Mockito.mock(IdentityAuthentication.class);

    final List<Permission> authPermissions = Arrays.asList(Permission.fromString("READ"));
    final List<OperateTenant> authTenants =
        Arrays.asList(new OperateTenant("tenantId", "tenantName"));

    when(identityAuthentication.getId()).thenReturn("mockId");
    when(identityAuthentication.getName()).thenReturn("mockName");
    when(identityAuthentication.getTenants()).thenReturn(authTenants);
    when(identityAuthentication.getPermissions()).thenReturn(authPermissions);

    final UserDto result = underTest.createUserDtoFrom(identityAuthentication);

    // Validate the DTO object was created with the expected fields
    assertThat(result).isNotNull();
    assertThat(result.getDisplayName()).isEqualTo(identityAuthentication.getName());
    assertThat(result.isCanLogout()).isTrue();
    assertThat(result.getPermissions()).isEqualTo(authPermissions);
    assertThat(result.getTenants()).isEqualTo(authTenants);
  }

  @Test
  public void testCreateUserDtoFromJwtToken() {
    final var jwtToken = Mockito.mock(JwtAuthenticationToken.class);
    final var mockJwt = Mockito.mock(Jwt.class);
    final var mockAuthentication = Mockito.mock(Authentication.class);
    final var mockAccessToken = Mockito.mock(AccessToken.class);

    when(jwtToken.getPrincipal()).thenReturn(mockJwt);
    when(jwtToken.getName()).thenReturn("mockTokenName");
    when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");
    when(mockIdentity.authentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.verifyToken(any())).thenReturn(mockAccessToken);
    when(mockAccessToken.getPermissions())
        .thenReturn(Arrays.asList(PermissionConverter.READ_PERMISSION_VALUE));
    when(mockPermissionConverter.convert(any())).thenCallRealMethod();

    final UserDto result = underTest.createUserDtoFrom(jwtToken);

    // Validate the DTO object was created with the expected fields
    assertThat(result).isNotNull();
    assertThat(result.getDisplayName()).isEqualTo(jwtToken.getName());
    assertThat(result.getUserId()).isEqualTo(jwtToken.getName());
    assertThat(result.isCanLogout()).isEqualTo(true);

    final List<Permission> permissions = result.getPermissions();
    assertThat(permissions).isNotNull();
    assertThat(permissions.size()).isEqualTo(1);
    assertThat(permissions.get(0)).isEqualTo(Permission.READ);
  }

  @Test
  public void testCreateDtoUserFromInvalidType() {
    final var tokenAuthentication = Mockito.mock(TokenAuthentication.class);

    final UserDto result = underTest.createUserDtoFrom(tokenAuthentication);

    assertThat(result).isNull();
  }

  @Test
  public void testCreateDtoUserFromNullType() {
    assertThat(underTest.createUserDtoFrom(null)).isNull();
  }
}
