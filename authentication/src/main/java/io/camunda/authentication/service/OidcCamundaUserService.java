/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaOAuthPrincipal;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.entity.ClusterMetadata.AppName;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@Profile("consolidated-auth")
public class OidcCamundaUserService implements CamundaUserService {
  private static final String SALES_PLAN_TYPE = "";

  // TODO: This needs to be set for SaaS purposes
  private static final Map<AppName, String> C8_LINKS = Map.of();

  private Optional<CamundaOAuthPrincipal> getCamundaUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final CamundaOAuthPrincipal user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    return getCamundaUser()
        .map(
            user -> {
              final AuthenticationContext auth = user.getAuthenticationContext();
              return new CamundaUserDTO(
                  auth.username(),
                  null,
                  user.getDisplayName(),
                  auth.username(),
                  user.getEmail(),
                  auth.authorizedApplications(),
                  auth.tenants(),
                  auth.groups(),
                  auth.roles().stream().map(RoleEntity::name).toList(),
                  SALES_PLAN_TYPE,
                  C8_LINKS,
                  true);
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return "";
  }
}
