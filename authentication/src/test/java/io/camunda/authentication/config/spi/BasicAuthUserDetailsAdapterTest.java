/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class BasicAuthUserDetailsAdapterTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserServices defaultUserServices;
  @Mock private UserServices tenantAUserServices;
  private BasicAuthUserDetailsAdapter adapter;

  @BeforeEach
  void setUp() {
    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.userServices("default", defaultUserServices)
                    .userServices("tenanta", tenantAUserServices));
    adapter = new BasicAuthUserDetailsAdapter(serviceRegistry);
    // Ensure no stale request context from a previous test
    RequestContextHolder.resetRequestAttributes();
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldMapUserEntityToCamundaUserDetails() {
    // given — no request context: defaults to "default" tenant
    when(defaultUserServices.getUser(any(), any()))
        .thenReturn(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1"));

    // when
    final var user = adapter.loadUser(TEST_USER_ID);

    // then
    assertThat(user).isNotNull();
    assertThat(user.username()).isEqualTo(TEST_USER_ID);
    assertThat(user.password()).isEqualTo("password1");
  }

  @Test
  void shouldResolveUserFromPhysicalTenantSetOnRequest() {
    // given — request context has PT id "tenanta"
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantAUserServices.getUser(any(), any()))
        .thenReturn(new UserEntity(200L, TEST_USER_ID, "PT User", "pt@tested", "pt-password"));

    // when
    final var user = adapter.loadUser(TEST_USER_ID);

    // then — user resolved from tenanta services
    assertThat(user).isNotNull();
    assertThat(user.username()).isEqualTo(TEST_USER_ID);
    assertThat(user.password()).isEqualTo("pt-password");
    verify(tenantAUserServices).getUser(any(), any());
  }

  @Test
  void shouldFallBackToDefaultTenantWhenNoRequestContextBound() {
    // given — no request context at all
    when(defaultUserServices.getUser(any(), any()))
        .thenReturn(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1"));

    // when
    final var user = adapter.loadUser(TEST_USER_ID);

    // then — default tenant services used
    assertThat(user).isNotNull();
    verify(defaultUserServices).getUser(any(), any());
  }

  @Test
  void shouldReturnNullWhenUserNotFound() {
    // given
    when(defaultUserServices.getUser(any(), any()))
        .thenThrow(new ServiceException("not found", Status.NOT_FOUND));

    // when
    final var user = adapter.loadUser(TEST_USER_ID);

    // then — CSL maps null to UsernameNotFoundException
    assertThat(user).isNull();
  }

  @Test
  void shouldPropagateNonNotFoundServiceException() {
    // given a real lookup failure (backend unavailable) rather than a missing user
    final var failure = new ServiceException("backend down", Status.UNAVAILABLE);
    when(defaultUserServices.getUser(any(), any())).thenThrow(failure);

    // when / then — must not be masked as a failed login (null); it propagates so it surfaces
    // as an error and is logged with its cause, instead of looking like a wrong password.
    assertThatThrownBy(() -> adapter.loadUser(TEST_USER_ID)).isSameAs(failure);
  }

  @Test
  void shouldPropagateUnexpectedRuntimeException() {
    // given
    final var failure = new RuntimeException("secondary storage down");
    when(defaultUserServices.getUser(any(), any())).thenThrow(failure);

    // when / then — not swallowed
    assertThatThrownBy(() -> adapter.loadUser(TEST_USER_ID)).isSameAs(failure);
  }

  @Test
  void shouldReturnNullForBlankUsername() {
    // when / then — no lookup attempted
    assertThat(adapter.loadUser("  ")).isNull();
    assertThat(adapter.loadUser(null)).isNull();
  }
}
