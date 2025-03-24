/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class CamundaOidcUser implements OidcUser, CamundaPrincipal, Serializable {
  private final OidcUser user;
  private final AuthenticationContext authentication;
  private final Set<Long> mappingKeys;
  private final Set<String> organizationIds;

  public CamundaOidcUser(
      final OidcUser oidcUser,
      final Set<Long> mappingKeys,
      final Set<String> organizationIds,
      final AuthenticationContext authentication) {
    user = oidcUser;
    this.mappingKeys = mappingKeys;
    this.organizationIds = organizationIds;
    this.authentication = authentication;
  }

  @Override
  public String getEmail() {
    return user.getEmail();
  }

  @Override
  public String getDisplayName() {
    return user.getPreferredUsername();
  }

  @Override
  public Set<String> getOrganizationIds() {
    return organizationIds;
  }

  @Override
  public AuthenticationContext getAuthenticationContext() {
    return authentication;
  }

  @Override
  public Map<String, Object> getClaims() {
    return user.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return user.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return user.getIdToken();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return user.getAttributes();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.getAuthorities();
  }

  @Override
  public String getName() {
    return user.getName();
  }

  public Set<Long> getMappingKeys() {
    return mappingKeys;
  }
}
