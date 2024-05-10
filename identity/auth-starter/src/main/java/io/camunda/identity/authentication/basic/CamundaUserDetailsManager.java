/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.authentication.basic;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
@Profile("identity-basic-auth")
public class CamundaUserDetailsManager extends JdbcUserDetailsManager {
  public static final String SELECT_USERNAMES_FROM_USERS = "SELECT username FROM users ";

  public CamundaUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableAuthorities(true);
  }

  public List<UserDetails> loadUsers() {
    return getJdbcTemplate().queryForList(SELECT_USERNAMES_FROM_USERS, String.class).stream()
        .map(this::loadUserByUsername)
        .toList();
  }

  @Override
  protected List<GrantedAuthority> loadUserAuthorities(final String username) {
    final List<GrantedAuthority> roles = super.loadUserAuthorities(username);
    return loadRolePrivileges(roles);
  }

  private List<GrantedAuthority> loadRolePrivileges(final List<GrantedAuthority> roles) {
    final List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.addAll(roles);
    authorities.addAll(
        roles.stream()
            .flatMap(
                role ->
                    getJdbcTemplate()
                        .query(
                            "SELECT authority FROM role_authorities WHERE role_name = ? ",
                            new String[] {role.getAuthority()},
                            (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString(1)))
                        .stream())
            .distinct()
            .toList());
    return authorities;
  }
}
