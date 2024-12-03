/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.entity.Permission;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsServiceTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserServices userService;
  @Mock private AuthorizationServices authorizationServices;
  @Mock private TenantServices tenantServices;
  private CamundaUserDetailsService userDetailsService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    userDetailsService =
        new CamundaUserDetailsService(userService, authorizationServices, tenantServices);
  }

  @Test
  public void testUserDetailsIsLoaded() {
    // given
    when(userService.search(any()))
        .thenReturn(
            new SearchQueryResult<>(
                1,
                List.of(new UserEntity(1L, TEST_USER_ID, "Foo Bar", "email@tested", "password1")),
                null));

    when(authorizationServices.search(any()))
        .thenReturn(
            new SearchQueryResult<>(
                1,
                List.of(
                    new AuthorizationEntity(
                        1L,
                        AuthorizationOwnerType.USER.name(),
                        AuthorizationResourceType.APPLICATION.name(),
                        List.of(
                            new Permission(PermissionType.ACCESS, Set.of("operate", "identity"))))),
                null));
    // when
    final CamundaUser user = (CamundaUser) userDetailsService.loadUserByUsername(TEST_USER_ID);

    // then
    assertThat(user).isInstanceOf(CamundaUser.class);
    assertThat(user.getUserKey()).isEqualTo(1L);
    assertThat(user.getName()).isEqualTo("Foo Bar");
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(user.getPassword()).isEqualTo("password1");
    assertThat(user.getEmail()).isEqualTo("email@tested");
    assertThat(user.getAuthorizedApplications()).containsExactlyInAnyOrder("operate", "identity");
  }

  @Test
  public void testUserDetailsNotFound() {
    // given
    when(userService.search(any()))
        .thenReturn(new SearchQueryResult<>(0, Collections.emptyList(), null));
    // when/then
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(TEST_USER_ID))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
