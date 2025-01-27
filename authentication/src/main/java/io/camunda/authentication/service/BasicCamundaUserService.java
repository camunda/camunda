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
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@Profile("consolidated-auth")
public class BasicCamundaUserService implements CamundaUserService {
  private Optional<CamundaUser> getCurrentCamundaUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final CamundaUser user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    return getCurrentCamundaUser()
        .map(
            user -> {
              final AuthenticationContext auth = user.getAuthenticationContext();
              return new CamundaUserDTO(
                  user.getUserId(),
                  user.getUserKey(),
                  user.getDisplayName(),
                  user.getDisplayName(), // migrated for historical purposes username -> displayName
                  user.getEmail(),
                  auth.authorizedApplications(),
                  auth.tenants(),
                  auth.groups(),
                  auth.roles().stream().map(RoleEntity::name).toList(),
                  user.getSalesPlanType(),
                  user.getC8Links(),
                  user.canLogout());
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return null;
  }
}
