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
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.DefaultServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDetailsAdapterTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserServices userServices;
  private UserDetailsAdapter adapter;

  @BeforeEach
  void setUp() {
    final var serviceRegistry =
        DefaultServiceRegistry.of(b -> b.userServices("default", userServices));
    adapter = new UserDetailsAdapter(serviceRegistry);
  }

  @Test
  void shouldMapUserEntityToCamundaUserDetails() {
    // given
    when(userServices.getUser(any(), any()))
        .thenReturn(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1"));

    // when
    final var user = adapter.loadUser(TEST_USER_ID);

    // then
    assertThat(user).isNotNull();
    assertThat(user.username()).isEqualTo(TEST_USER_ID);
    assertThat(user.password()).isEqualTo("password1");
  }

  @Test
  void shouldReturnNullWhenUserNotFound() {
    // given
    when(userServices.getUser(any(), any()))
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
    when(userServices.getUser(any(), any())).thenThrow(failure);

    // when / then — must not be masked as a failed login (null); it propagates so it surfaces
    // as an error and is logged with its cause, instead of looking like a wrong password.
    assertThatThrownBy(() -> adapter.loadUser(TEST_USER_ID)).isSameAs(failure);
  }

  @Test
  void shouldPropagateUnexpectedRuntimeException() {
    // given
    final var failure = new RuntimeException("secondary storage down");
    when(userServices.getUser(any(), any())).thenThrow(failure);

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
