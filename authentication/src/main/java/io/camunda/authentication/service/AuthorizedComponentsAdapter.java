/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * OC adapter for CSL's {@link AuthorizedComponentsPort}. CSL's default {@code
 * OidcCamundaUserService} needs the list of webapp components the principal can use, but the
 * resource-access framework that holds that data still lives in OC; this adapter bridges the gap
 * until {@code ResourceAccessProvider} itself migrates.
 *
 * <p>Resolves {@code COMPONENT_ACCESS_AUTHORIZATION} via the host's {@link ResourceAccessProvider}
 * and renames the {@code identity} resource id to {@code admin} — a long-standing OC-side alias
 * preserved here so the {@code /v2/authentication/me} response shape does not change.
 *
 * <p>{@code @ConditionalOnSecondaryStorageEnabled} matches the gate on the resource-access stack
 * itself: without secondary storage there is no authorization data to query.
 */
@Service
@ConditionalOnSecondaryStorageEnabled
public final class AuthorizedComponentsAdapter implements AuthorizedComponentsPort {

  private final ResourceAccessProvider resourceAccessProvider;

  public AuthorizedComponentsAdapter(final ResourceAccessProvider resourceAccessProvider) {
    this.resourceAccessProvider = resourceAccessProvider;
  }

  @Override
  public List<String> resolve(final CamundaAuthentication authentication) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    if (!componentAccess.allowed()) {
      return List.of();
    }
    final var resourceIds = componentAccess.authorization().resourceIds();
    if (resourceIds == null) {
      return List.of();
    }
    return resourceIds.stream().map(id -> "identity".equals(id) ? "admin" : id).distinct().toList();
  }
}
