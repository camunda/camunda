/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.security;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class CamundaUserDetailsManager extends JdbcUserDetailsManager {
  public CamundaUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableGroups(true);
    setEnableAuthorities(true);
  }

  @Override
  protected List<UserDetails> loadUsersByUsername(final String username) {
    return super.loadUsersByUsername(username).stream()
        .map(u -> (UserDetails) new CamundaUserDetails(u))
        .toList();
  }

  @Override
  protected void addCustomAuthorities(
      final String username, final List<GrantedAuthority> authorities) {
    super.addCustomAuthorities(username, authorities);
    final List<GrantedAuthority> permissions = loadRolesPrivilages(authorities);
    authorities.addAll(permissions);
  }

  @Override
  public CamundaUserDetails loadUserByUsername(final String username)
      throws UsernameNotFoundException {
    return new CamundaUserDetails(super.loadUserByUsername(username));
  }

  private List<GrantedAuthority> loadRolesPrivilages(final List<GrantedAuthority> roles) {
    final List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.addAll(
        roles.stream()
            .flatMap(
                role ->
                    getJdbcTemplate()
                        .query(
                            "select p.definition from roles_permissions rp "
                                + "join permissions p on p.id = rp.permissions_id "
                                + " where role_authority = ? ",
                            new String[] {role.getAuthority()},
                            (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString(1)))
                        .stream())
            .distinct()
            .toList());
    return authorities;
  }
}
