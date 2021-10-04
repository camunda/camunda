/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

  private String firstname;
  private String lastname;

  private List<Role> roles;

  public User(String username, String password, List<Role> roles) {
    super(username, password, toAuthorities(roles));
    this.roles = roles;
  }

  public String getFirstname() {
    return firstname;
  }

  public User setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public String getLastname() {
    return lastname;
  }

  public User setLastname(String lastname) {
    this.lastname = lastname;
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
    return Objects.equals(firstname, user.firstname)
        && Objects.equals(lastname, user.lastname)
        && Objects.equals(roles, user.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), firstname, lastname, roles);
  }
}
