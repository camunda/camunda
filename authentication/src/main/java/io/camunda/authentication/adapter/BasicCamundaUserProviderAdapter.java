/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.UserServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageEnabled
public final class BasicCamundaUserProviderAdapter implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final UserServices userServices;

  public BasicCamundaUserProviderAdapter(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final UserServices userServices) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.userServices = userServices;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(
            auth -> {
              final var user =
                  userServices.getUser(
                      auth.authenticatedUsername(), CamundaAuthentication.anonymous());
              final var authorizedComponents = getAuthorizedComponents(auth);
              final var tenantIds =
                  auth.authenticatedTenantIds() != null
                      ? auth.authenticatedTenantIds()
                      : List.<String>of();
              return new CamundaUserInfo(
                  user.name(),
                  auth.authenticatedUsername(),
                  user.email(),
                  authorizedComponents,
                  tenantIds,
                  auth.authenticatedGroupIds(),
                  auth.authenticatedRoleIds(),
                  true);
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return null;
  }

  private List<String> getAuthorizedComponents(
      final io.camunda.gatekeeper.model.identity.CamundaAuthentication authentication) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    return componentAccess.allowed() ? componentAccess.authorization().resourceIds() : List.of();
  }
}
