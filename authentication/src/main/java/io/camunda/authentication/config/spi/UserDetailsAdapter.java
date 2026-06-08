/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.search.entities.UserEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.out.UserDetailsPort;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.registry.ServiceRegistry;

/**
 * Host-supplied {@link UserDetailsPort} resolving basic-auth users from OC's user services,
 * replacing the hand-rolled {@code CamundaUserDetailsService} (camunda/camunda-security-library#372).
 *
 * <p>An unknown user ({@link ServiceException.Status#NOT_FOUND}) resolves to {@code null}, the
 * port's no-such-user signal; any other lookup failure propagates rather than being masked as a
 * missing user.
 */
public final class UserDetailsAdapter implements UserDetailsPort {

  private final ServiceRegistry serviceRegistry;

  public UserDetailsAdapter(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Override
  public CamundaUserDetails loadUser(final String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    final UserEntity user = getUser(username);
    if (user == null) {
      return null;
    }
    return new CamundaUserDetails(user.username(), user.password());
  }

  private UserEntity getUser(final String username) {
    try {
      // TODO physical-tenant resolution is a later slice (camunda/camunda#54729, #54730); this
      // adapter is its home. Until then basic-auth users are resolved from the "default" tenant.
      return serviceRegistry
          .userServices("default")
          .getUser(username, CamundaAuthentication.anonymous());
    } catch (final ServiceException e) {
      if (e.getStatus() == ServiceException.Status.NOT_FOUND) {
        // No such user: the port's null signal.
        return null;
      }
      // A real lookup failure (e.g. backend unavailable) must not be masked as a missing user.
      throw e;
    }
  }
}
