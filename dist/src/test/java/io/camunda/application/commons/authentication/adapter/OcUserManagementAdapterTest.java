/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcUserManagementAdapterTest {

  private final UserServices userServices = mock(UserServices.class);
  private final UserServices authenticatedServices = mock(UserServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcUserManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(userServices.withAuthentication(authentication)).thenReturn(authenticatedServices);
    adapter = new OcUserManagementAdapter(userServices, authProvider);
  }

  @Test
  void authenticatesEveryCall() {
    when(authenticatedServices.getUser("alice"))
        .thenReturn(new UserEntity(1L, "alice", "Alice", "a@b.com", null));

    adapter.getByUsername("alice");

    verify(authProvider).getCamundaAuthentication();
    verify(userServices).withAuthentication(authentication);
  }

  @Nested
  class GetByUsername {

    @Test
    void delegatesToUserServicesGetUser() {
      final var entity = new UserEntity(42L, "alice", "Alice A.", "alice@example.com", null);
      when(authenticatedServices.getUser("alice")).thenReturn(entity);

      final AuthUser result = adapter.getByUsername("alice");

      assertThat(result.userKey()).isEqualTo(42L);
      assertThat(result.username()).isEqualTo("alice");
      assertThat(result.name()).isEqualTo("Alice A.");
      assertThat(result.email()).isEqualTo("alice@example.com");
      assertThat(result.password()).isNull();
      verify(authenticatedServices).getUser("alice");
    }

    @Test
    void mapsNullUserKeyToZero() {
      final var entity = new UserEntity(null, "bob", "Bob B.", "bob@example.com", null);
      when(authenticatedServices.getUser("bob")).thenReturn(entity);

      final AuthUser result = adapter.getByUsername("bob");

      assertThat(result.userKey()).isEqualTo(0L);
    }
  }

  @Nested
  class Create {

    @Test
    void delegatesToUserServicesCreateUser() {
      when(authenticatedServices.createUser(any(UserDTO.class)))
          .thenReturn(CompletableFuture.completedFuture(null));

      final AuthUser result = adapter.create("alice", "secret", "Alice A.", "alice@example.com");

      assertThat(result.userKey()).isEqualTo(0L);
      assertThat(result.username()).isEqualTo("alice");
      assertThat(result.name()).isEqualTo("Alice A.");
      assertThat(result.email()).isEqualTo("alice@example.com");
      assertThat(result.password()).isNull();

      final var captor = ArgumentCaptor.forClass(UserDTO.class);
      verify(authenticatedServices).createUser(captor.capture());
      final UserDTO dto = captor.getValue();
      assertThat(dto.username()).isEqualTo("alice");
      assertThat(dto.name()).isEqualTo("Alice A.");
      assertThat(dto.email()).isEqualTo("alice@example.com");
      assertThat(dto.password()).isEqualTo("secret");
    }

    @Test
    void unwrapsCompletionExceptionWithRuntimeCause() {
      final var rootCause = new IllegalArgumentException("duplicate user");
      when(authenticatedServices.createUser(any(UserDTO.class)))
          .thenReturn(CompletableFuture.failedFuture(rootCause));

      assertThatThrownBy(() -> adapter.create("alice", "pw", "Alice", "a@b.com"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("duplicate user");
    }

    @Test
    void wrapsCheckedExceptionInRuntimeException() {
      final var rootCause = new Exception("checked exception");
      when(authenticatedServices.createUser(any(UserDTO.class)))
          .thenReturn(CompletableFuture.failedFuture(rootCause));

      assertThatThrownBy(() -> adapter.create("alice", "pw", "Alice", "a@b.com"))
          .isInstanceOf(RuntimeException.class)
          .hasCause(rootCause);
    }
  }

  @Nested
  class Update {

    @Test
    void delegatesToUserServicesUpdateUser() {
      when(authenticatedServices.updateUser(any(UserDTO.class)))
          .thenReturn(CompletableFuture.completedFuture(null));

      final AuthUser result = adapter.update("alice", "Alice Updated", "alice-new@example.com");

      assertThat(result.username()).isEqualTo("alice");
      assertThat(result.name()).isEqualTo("Alice Updated");
      assertThat(result.email()).isEqualTo("alice-new@example.com");

      final var captor = ArgumentCaptor.forClass(UserDTO.class);
      verify(authenticatedServices).updateUser(captor.capture());
      final UserDTO dto = captor.getValue();
      assertThat(dto.username()).isEqualTo("alice");
      assertThat(dto.name()).isEqualTo("Alice Updated");
      assertThat(dto.email()).isEqualTo("alice-new@example.com");
      assertThat(dto.password()).isNull();
    }

    @Test
    void unwrapsCompletionExceptionWithRuntimeCause() {
      final var rootCause = new IllegalStateException("not found");
      when(authenticatedServices.updateUser(any(UserDTO.class)))
          .thenReturn(CompletableFuture.failedFuture(rootCause));

      assertThatThrownBy(() -> adapter.update("alice", "Alice", "a@b.com"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("not found");
    }
  }

  @Nested
  class Delete {

    @Test
    void delegatesToUserServicesDeleteUser() {
      when(authenticatedServices.deleteUser("alice"))
          .thenReturn(CompletableFuture.completedFuture(null));

      adapter.delete("alice");

      verify(authenticatedServices).deleteUser("alice");
    }

    @Test
    void unwrapsCompletionExceptionWithRuntimeCause() {
      final var rootCause = new IllegalStateException("not found");
      when(authenticatedServices.deleteUser("alice"))
          .thenReturn(CompletableFuture.failedFuture(rootCause));

      assertThatThrownBy(() -> adapter.delete("alice"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("not found");
    }
  }
}
