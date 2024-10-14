/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import java.util.Collections;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CamundaUser extends User {

  private final Long userKey;
  private final String displayName;

  private boolean canLogout = true;

  public CamundaUser(
      final Long userKey, final String displayName, final String username, final String password) {
    super(username, password, Collections.emptyList());
    this.userKey = userKey;
    this.displayName = displayName;
  }

  public CamundaUser(
      final String displayName,
      final String username,
      final String password,
      final List<String> roles) {
    super(username, password, prepareAuthorities(roles));
    userKey = null;
    this.displayName = displayName;
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

  public List<String> getRoles() {
    return getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  public boolean isCanLogout() {
    return canLogout;
  }

  private static List<SimpleGrantedAuthority> prepareAuthorities(final List<String> roles) {
    return roles.stream().map(SimpleGrantedAuthority::new).toList();
  }

  public static final class CamundaUserBuilder {
    private Long userKey;
    private String name;
    private String username;
    private String password;

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

    public CamundaUser build() {
      return new CamundaUser(userKey, name, username, password);
    }
  }
}
