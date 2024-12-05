/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.AuthProfile;
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.RoleEntity;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Profile("auth-basic")
@Service
public class BasicCamundaUserService implements CamundaUserService {
  private static final Logger LOG = LoggerFactory.getLogger(BasicCamundaUserService.class);

  @Override
  public AuthProfile getProfile() {
    return AuthProfile.BASIC;
  }

  @Override
  public @Nullable CamundaUserDTO getCurrentUser() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    final Object principal = authentication.getPrincipal();
    if (!(principal instanceof final CamundaUser authenticatedUser)) {
      LOG.error("Principal {} is not a CamundaUser", principal);
      return null;
    }
    return new CamundaUserDTO(
        authenticatedUser.getUserId(),
        authenticatedUser.getUserKey(),
        authenticatedUser.getDisplayName(),
        authenticatedUser
            .getDisplayName(), // migrated for historical purposes username -> displayName
        authenticatedUser.getEmail(),
        authenticatedUser.getAuthorizedApplications(),
        authenticatedUser.getTenants(),
        authenticatedUser.getGroups(),
        authenticatedUser.getRoles().stream().map(RoleEntity::name).toList(),
        authenticatedUser.getSalesPlanType(),
        authenticatedUser.getC8Links(),
        authenticatedUser.canLogout());
  }

  @Override
  public String getUserToken() {
    return null;
  }
}
