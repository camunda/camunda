/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.port.inbound.UserManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import java.util.concurrent.CompletionException;

/**
 * Bridges the auth library's {@link UserManagementPort} to the monorepo's {@link UserServices}.
 * Write operations are dispatched to Zeebe via the broker client; read operations query the search
 * infrastructure.
 */
public class OcUserManagementAdapter implements UserManagementPort {

  private final UserServices userServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcUserManagementAdapter(
      final UserServices userServices, final CamundaAuthenticationProvider authProvider) {
    this.userServices = userServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthUser getByUsername(final String username) {
    final var entity = userServices.withAuthentication(auth()).getUser(username);
    return toAuthUser(entity);
  }

  @Override
  public AuthUser create(
      final String username, final String password, final String name, final String email) {
    try {
      userServices
          .withAuthentication(auth())
          .createUser(new UserDTO(username, name, email, password))
          .join();
      return new AuthUser(0L, username, name, email, null);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthUser update(final String username, final String name, final String email) {
    try {
      userServices
          .withAuthentication(auth())
          .updateUser(new UserDTO(username, name, email, null))
          .join();
      return new AuthUser(0L, username, name, email, null);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String username) {
    try {
      userServices.withAuthentication(auth()).deleteUser(username).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthUser toAuthUser(final UserEntity entity) {
    return new AuthUser(
        entity.userKey() != null ? entity.userKey() : 0L,
        entity.username(),
        entity.name(),
        entity.email(),
        null);
  }

  private static RuntimeException mapException(final Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new RuntimeException(cause);
  }
}
