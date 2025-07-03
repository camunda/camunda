/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CamundaAuthenticationFacade implements CamundaAuthentication {

  private final CamundaAuthentication delegate;
  private final DeferredMembershipResolver resolver;
  private MembershipContext resolvedMembershipContext;

  public CamundaAuthenticationFacade(
      final CamundaAuthentication delegate, final DeferredMembershipResolver resolver) {
    this.delegate = delegate;
    this.resolver = resolver;
  }

  @Override
  public String getEmail() {
    return delegate.getEmail();
  }

  @Override
  public CamundaAuthentication setEmail(final String email) {
    delegate.setEmail(email);
    return this;
  }

  @Override
  public String getDisplayName() {
    return delegate.getDisplayName();
  }

  @Override
  public CamundaAuthentication setDisplayName(final String displayName) {
    delegate.setDisplayName(displayName);
    return this;
  }

  @Override
  public String getUsername() {
    return delegate.getUsername();
  }

  @Override
  public CamundaAuthentication setUsername(final String username) {
    delegate.setUsername(username);
    return this;
  }

  @Override
  public String getClientId() {
    return delegate.getClientId();
  }

  @Override
  public CamundaAuthentication setClientId(final String clientId) {
    delegate.setClientId(clientId);
    return this;
  }

  @Override
  public List<String> getGroupIds() {
    if (delegate.getGroupIds() == null) {
      resolveMemberships();
    }
    return delegate.getGroupIds();
  }

  @Override
  public CamundaAuthentication setGroupIds(final List<String> groups) {
    delegate.setGroupIds(groups);
    return this;
  }

  @Override
  public List<String> getRoleIds() {
    if (delegate.getRoleIds() == null) {
      resolveMemberships();
    }
    return delegate.getRoleIds();
  }

  @Override
  public CamundaAuthentication setRoleIds(final List<String> roles) {
    delegate.setRoleIds(roles);
    return this;
  }

  @Override
  public List<String> getTenantIds() {
    if (delegate.getTenantIds() == null) {
      resolveMemberships();
    }
    return delegate.getTenantIds();
  }

  @Override
  public CamundaAuthentication setTenantIds(final List<String> tenants) {
    delegate.setTenantIds(tenants);
    return this;
  }

  @Override
  public List<String> getMappingIds() {
    if (delegate.getMappingIds() == null) {
      resolveMemberships();
    }
    return delegate.getMappingIds();
  }

  @Override
  public CamundaAuthentication setMappingIds(final List<String> mappings) {
    delegate.setMappingIds(mappings);
    return this;
  }

  @Override
  public Map<String, Object> getClaims() {
    return delegate.getClaims();
  }

  @Override
  public CamundaAuthentication setClaims(final Map<String, Object> claims) {
    delegate.setClaims(claims);
    return this;
  }

  protected void resolveMemberships() {
    final var memberships = resolver.resolveMemberships(delegate);
    synchronized (delegate) {
      delegate.setGroupIds(Objects.requireNonNullElse(memberships.groupIds(), List.of()));
      delegate.setRoleIds(Objects.requireNonNullElse(memberships.roleIds(), List.of()));
      delegate.setTenantIds(Objects.requireNonNullElse(memberships.tenantIds(), List.of()));
      delegate.setMappingIds(Objects.requireNonNullElse(memberships.mappingIds(), List.of()));
    }
  }
}
