/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.AssigneeMigratorNoImpl;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Temporary configuration required to start Tasklist as part of C8 single application.
 *
 * <p>Tasklist security package is excluded from the configuration of C8 single application to avoid
 * the conflicts with the existing Operate WebSecurity configuration. This will be solved after the
 * creation of a common Security layer.
 *
 * <p>For now, only default AUTH authentication is supported for Tasklist when run in C8 single
 * application.
 *
 * <p>TasklistSecurityStubsConfiguration provides the security related bean stubs required by the
 * service layer of Tasklist.
 */
@Configuration(proxyBeanMethods = false)
@Profile("tasklist & operate")
public class TasklistSecurityStubsConfiguration {
  /** UserReader that gets user details using Operate's UserService */
  @Bean
  public UserReader stubUserReader(final UserService userService) {
    return new UserReader() {

      @Override
      public UserDTO getCurrentUser() {
        final var operateUserDto = userService.getCurrentUser();
        return new UserDTO()
            .setUserId(operateUserDto.getUserId())
            .setDisplayName(operateUserDto.getDisplayName())
            .setPermissions(List.of(Permission.READ, Permission.WRITE));
      }

      @Override
      public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
        return Optional.empty();
      }

      @Override
      public String getCurrentOrganizationId() {
        return DEFAULT_ORGANIZATION;
      }

      @Override
      public String getCurrentUserId() {
        return getCurrentUser().getUserId();
      }

      /** used for GraphQL only */
      @Override
      public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
        return List.of();
      }

      /** used in SSO only */
      @Override
      public Optional<String> getUserToken(final Authentication authentication) {
        return Optional.empty();
      }
    };
  }

  @Bean
  public TenantService stubTenantService() {
    return new TenantService() {
      @Override
      public List<String> tenantsIds() {
        final List<String> authenticatedTenantIds = new ArrayList<>();
        final var requestAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (requestAuthentication.getPrincipal()
            instanceof final CamundaPrincipal authenticatedPrincipal) {

          authenticatedTenantIds.addAll(
              authenticatedPrincipal.getAuthenticationContext().tenants().stream()
                  .map(TenantDTO::tenantId)
                  .toList());
        }
        return authenticatedTenantIds;
      }

      @Override
      public AuthenticatedTenants getAuthenticatedTenants() {
        return AuthenticatedTenants.allTenants();
      }

      @Override
      public boolean isTenantValid(final String tenantId) {
        return true;
      }

      @Override
      public boolean isMultiTenancyEnabled() {
        return false;
      }
    };
  }

  @Bean
  public AssigneeMigrator stubAssigneeMigrator() {
    return new AssigneeMigratorNoImpl();
  }

  @Bean
  public TasklistProfileService stubTasklistProfileService() {
    return new TasklistProfileService() {

      @Override
      public String getMessageByProfileFor(final Exception exception) {
        return "";
      }

      @Override
      public boolean currentProfileCanLogout() {
        return true;
      }

      @Override
      public boolean isLoginDelegated() {
        return false;
      }
    };
  }
}
