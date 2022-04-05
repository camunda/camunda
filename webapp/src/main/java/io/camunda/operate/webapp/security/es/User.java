/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.es;

import static io.camunda.operate.util.CollectionUtil.map;

import io.camunda.operate.webapp.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class User extends org.springframework.security.core.userdetails.User {

  private String userId;

  private String displayName;
  private List<Role> roles;
  private boolean canLogout = true;

  private static Collection<? extends GrantedAuthority> toAuthorities(List<Role> roles) {
    return map(roles, role -> new SimpleGrantedAuthority("ROLE_" + role));
  }

  public User(String userId,String displayName, String password, List<Role> roles) {
    super(userId, password, toAuthorities(roles));
    this.userId = userId;
    this.displayName = displayName;
    this.roles = roles;
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

  public boolean isCanLogout() {
    return canLogout;
  }

  public User setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public User setRoles(List<Role> roles) {
    this.roles = roles;
    return this;
  }
  public List<Role> getRoles() {
    return roles;
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
    return canLogout == user.canLogout && userId.equals(user.userId) && displayName.equals(
        user.displayName) && roles.equals(user.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, displayName, roles, canLogout);
  }
}
