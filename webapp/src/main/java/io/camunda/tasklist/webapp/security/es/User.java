/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.webapp.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class User extends org.springframework.security.core.userdetails.User {

  private String userId;
  private String displayName;

  private List<Role> roles;

  public User(String username, String password, List<Role> roles) {
    super(username, password, toAuthorities(roles));
    this.roles = roles;
    this.userId = username;
  }

  public String getUserId() {
    return userId;
  }

  public User setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public User setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  public User setRoles(List<Role> roles) {
    this.roles = roles;
    return this;
  }

  public List<Role> getRoles() {
    return roles;
  }

  private static Collection<? extends GrantedAuthority> toAuthorities(List<Role> roles) {
    return map(roles, role -> new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final User user = (User) o;
    return Objects.equals(userId, user.userId)
        && Objects.equals(displayName, user.displayName)
        && Objects.equals(roles, user.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, displayName, roles);
  }
}
