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
import io.camunda.security.core.port.out.BasicAuthUserDetailsPort;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;

/**
 * Host-supplied {@link BasicAuthUserDetailsPort} resolving basic-auth users from OC's user
 * services, replacing the hand-rolled {@code CamundaUserDetailsService}
 * (camunda/camunda-security-library#372).
 *
 * <p>The physical tenant id is resolved via {@link PhysicalTenantContext#current()}, which reads
 * the id stamped on the request by {@code PhysicalTenantFilter} (gateway-rest), registered to run
 * before Spring Security's filter chain. For non-prefixed requests the context falls back to {@link
 * PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID}.
 *
 * <p>An unknown user ({@link ServiceException.Status#NOT_FOUND}) resolves to {@code null}, the
 * port's no-such-user signal; any other lookup failure propagates rather than being masked as a
 * missing user.
 */
public final class BasicAuthUserDetailsAdapter implements BasicAuthUserDetailsPort {

  private final ServiceRegistry serviceRegistry;

  public BasicAuthUserDetailsAdapter(final ServiceRegistry serviceRegistry) {
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
      final String physicalTenantId = PhysicalTenantContext.current();
      return serviceRegistry
          .userServices(physicalTenantId)
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
