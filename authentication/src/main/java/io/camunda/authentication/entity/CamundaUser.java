/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.search.entities.RoleEntity;
import io.camunda.security.entity.ClusterMetadata;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class CamundaUser extends User {

  private final Long userKey;
  private final String displayName;
  private final List<String> authorizedApplications;
  private final List<TenantDTO> tenants;
  private final List<String> groups;
  private final List<RoleEntity> roles;
  private final String salesPlanType;
  private final Map<ClusterMetadata.AppName, String> c8Links;
  private final boolean canLogout;
  private final boolean apiUser;
  private final String email;

  private CamundaUser(
      final Long userKey,
      final String displayName,
      final String username,
      final String password,
      final String email,
      final List<? extends GrantedAuthority> authorities,
      final List<RoleEntity> roles,
      final List<String> authorizedApplications,
      final List<TenantDTO> tenants,
      final List<String> groups,
      final String salesPlanType,
      final Map<ClusterMetadata.AppName, String> c8Links,
      final boolean canLogout,
      final boolean apiUser) {
    super(username, password, authorities);
    this.roles = roles;
    this.userKey = userKey;
    this.displayName = displayName;
    this.authorizedApplications = authorizedApplications;
    this.tenants = tenants;
    this.groups = groups;
    this.salesPlanType = salesPlanType;
    this.c8Links = Objects.requireNonNullElse(c8Links, Collections.emptyMap());
    this.canLogout = canLogout;
    this.apiUser = apiUser;
    this.email = email;
  }

  public Long getUserKey() {
    return userKey;
  }

  public String getName() {
    return displayName;
  }

  public String getUserId() {
    return getUsername();
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<RoleEntity> getRoles() {
    return roles;
  }

  public List<String> getGroups() {
    return groups;
  }

  public List<String> getAuthorizedApplications() {
    return authorizedApplications;
  }

  public List<TenantDTO> getTenants() {
    return tenants;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public Map<ClusterMetadata.AppName, String> getC8Links() {
    return c8Links;
  }

  public boolean canLogout() {
    return canLogout;
  }

  public boolean isApiUser() {
    return apiUser;
  }

  private static List<? extends GrantedAuthority> prepareAuthorities(
      final List<String> authorities) {
    return authorities.stream().map(SimpleGrantedAuthority::new).toList();
  }

  public String getEmail() {
    return email;
  }

  public static final class CamundaUserBuilder {
    private Long userKey;
    private String name;
    private String username;
    private String password;
    private String email;
    private List<RoleEntity> roles = List.of();
    private List<? extends GrantedAuthority> authorities = List.of();
    private List<String> authorizedApplications = List.of();
    private List<TenantDTO> tenants = List.of();
    private List<String> groups = List.of();
    private String salesPlanType;
    private Map<ClusterMetadata.AppName, String> c8Links = Map.of();
    private boolean canLogout;
    private boolean apiUser;

    private CamundaUserBuilder() {}

    public static CamundaUserBuilder aCamundaUser() {
      return new CamundaUserBuilder();
    }

    public CamundaUserBuilder withUserKey(final Long userKey) {
      this.userKey = userKey;
      return this;
    }

    public CamundaUserBuilder withName(final String name) {
      this.name = name;
      return this;
    }

    public CamundaUserBuilder withUsername(final String username) {
      this.username = username;
      return this;
    }

    public CamundaUserBuilder withPassword(final String password) {
      this.password = password;
      return this;
    }

    public CamundaUserBuilder withEmail(final String email) {
      this.email = email;
      return this;
    }

    public CamundaUserBuilder withRoles(final List<RoleEntity> roles) {
      this.roles = Objects.requireNonNullElse(roles, List.of());
      return this;
    }

    public CamundaUserBuilder withAuthorities(final List<String> authorities) {
      this.authorities = prepareAuthorities(Objects.requireNonNullElse(authorities, List.of()));
      return this;
    }

    public CamundaUserBuilder withAuthorizedApplications(
        final List<String> authorizedApplications) {
      this.authorizedApplications = Objects.requireNonNullElse(authorizedApplications, List.of());
      return this;
    }

    public CamundaUserBuilder withTenants(final List<TenantDTO> tenants) {
      this.tenants = Objects.requireNonNullElse(tenants, List.of());
      return this;
    }

    public CamundaUserBuilder withGroups(final List<String> groups) {
      this.groups = Objects.requireNonNullElse(groups, List.of());
      return this;
    }

    public CamundaUserBuilder withSalesPlanType(final String salesPlanType) {
      this.salesPlanType = salesPlanType;
      return this;
    }

    public CamundaUserBuilder withC8Links(final Map<ClusterMetadata.AppName, String> c8Links) {
      this.c8Links = Objects.requireNonNullElse(c8Links, Map.of());
      return this;
    }

    public CamundaUserBuilder withCanLogout(final boolean canLogout) {
      this.canLogout = canLogout;
      return this;
    }

    public CamundaUserBuilder withApiUser(final boolean apiUser) {
      this.apiUser = apiUser;
      return this;
    }

    public CamundaUser build() {
      return new CamundaUser(
          userKey,
          name,
          username,
          password,
          email,
          authorities,
          roles,
          authorizedApplications,
          tenants,
          groups,
          salesPlanType,
          c8Links,
          canLogout,
          apiUser);
    }
  }
}
