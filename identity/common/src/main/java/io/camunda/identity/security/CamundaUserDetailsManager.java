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
    final List<GrantedAuthority> permissions = loadPermissionsOfRoles(authorities);
    authorities.addAll(permissions);
  }

  @Override
  public CamundaUserDetails loadUserByUsername(final String username)
      throws UsernameNotFoundException {
    return new CamundaUserDetails(super.loadUserByUsername(username));
  }

  private List<GrantedAuthority> loadPermissionsOfRoles(final List<GrantedAuthority> roles) {
    final List<GrantedAuthority> permissions = new ArrayList<>();

    for (final GrantedAuthority role : roles) {
      final List<String> permissionsForRole =
          getJdbcTemplate()
              .queryForList(
                  "select p.definition "
                      + "from roles_permissions rp join permissions p on rp.permission_id = p.id "
                      + "where rp.role_authority = ?",
                  String.class,
                  role.getAuthority().replace("ROLE_", ""));

      permissions.addAll(permissionsForRole.stream().map(SimpleGrantedAuthority::new).toList());
    }

    return permissions.stream().distinct().toList();
  }
}
