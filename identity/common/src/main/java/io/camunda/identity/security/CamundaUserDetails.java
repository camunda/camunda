/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CamundaUserDetails implements UserDetails {
  private UserDetails user;

  //  These fields are here to support a migratory period between the Operate security classes
  //  and the central auth layer, they may move in the future.
  private final Long userId;
  private final String displayName;

  public CamundaUserDetails(final UserDetails user, final Long userId, final String displayName) {
    this.user = user;
    this.userId = userId;
    this.displayName = displayName;
  }

  public CamundaUserDetails(final Long userId, final String displayName) {
    this.userId = userId;
    this.displayName = displayName;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.getAuthorities();
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getUsername();
  }

  @Override
  public boolean isAccountNonExpired() {
    return user.isAccountNonExpired();
  }

  @Override
  public boolean isAccountNonLocked() {
    return user.isAccountNonLocked();
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return user.isCredentialsNonExpired();
  }

  @Override
  public boolean isEnabled() {
    return user.isEnabled();
  }

  public Long getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<String> getRoles() {
    return user.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith("ROLE_"))
        .map(roleName -> roleName.replace("ROLE_", ""))
        .toList();
  }

  public List<String> getPermissions() {
    return user.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> !authority.startsWith("ROLE_"))
        .toList();
  }
}
