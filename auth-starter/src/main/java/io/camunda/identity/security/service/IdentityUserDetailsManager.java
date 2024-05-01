/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.security.service;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class IdentityUserDetailsManager extends JdbcUserDetailsManager {
  public IdentityUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableGroups(true);
    setEnableAuthorities(true);
  }

  public void createGroup(
      final String groupName,
      final String organizationId,
      final List<GrantedAuthority> authorities) {
    super.createGroup(groupName, authorities);
    getJdbcTemplate()
        .update(
            "update groups set organization_id = ? where group_name = ?",
            organizationId,
            groupName);
  }

  @Override
  protected List<GrantedAuthority> loadUserAuthorities(final String username) {
    final List<GrantedAuthority> roles = super.loadUserAuthorities(username);
    return loadRolePrivileges(roles);
  }

  @Override
  protected List<GrantedAuthority> loadGroupAuthorities(final String username) {
    final List<GrantedAuthority> roles = super.loadGroupAuthorities(username);
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
                            "select authority from role_authorities where role_name = ? ",
                            new String[] {role.getAuthority()},
                            (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString(1)))
                        .stream())
            .distinct()
            .toList());
    return authorities;
  }
}
