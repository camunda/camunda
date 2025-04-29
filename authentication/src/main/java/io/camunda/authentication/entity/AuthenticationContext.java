/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.search.entities.RoleEntity;
import io.camunda.service.TenantServices.TenantDTO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record AuthenticationContext(
    String username,
    String applicationId,
    List<RoleEntity> roles,
    List<String> authorizedApplications,
    List<TenantDTO> tenants,
    List<String> groups)
    implements Serializable {

  public static final class AuthenticationContextBuilder {
    private String username;
    private String applicationId;
    private List<RoleEntity> roles = new ArrayList<>();
    private List<String> authorizedApplications = new ArrayList<>();
    private List<TenantDTO> tenants = new ArrayList<>();
    private List<String> groups = new ArrayList<>();

    public AuthenticationContextBuilder withUsername(final String username) {
      this.username = username;
      return this;
    }

    public AuthenticationContextBuilder withApplicationId(final String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public AuthenticationContextBuilder withRoles(final List<RoleEntity> roles) {
      this.roles = roles;
      return this;
    }

    public AuthenticationContextBuilder withAuthorizedApplications(
        final List<String> authorizedApplications) {
      this.authorizedApplications = authorizedApplications;
      return this;
    }

    public AuthenticationContextBuilder withTenants(final List<TenantDTO> tenants) {
      this.tenants = tenants;
      return this;
    }

    public AuthenticationContextBuilder withGroups(final List<String> groups) {
      this.groups = groups;
      return this;
    }

    public AuthenticationContext build() {
      return new AuthenticationContext(
          username, applicationId, roles, authorizedApplications, tenants, groups);
    }
  }
}
