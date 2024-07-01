/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.security;

import io.camunda.identity.usermanagement.repository.UserProfileRepository;
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
  private final UserProfileRepository userProfileRepository;

  public CamundaUserDetailsManager(
      final DataSource dataSource, final UserProfileRepository userProfileRepository) {
    super(dataSource);
    setEnableGroups(true);
    setEnableAuthorities(true);
    this.userProfileRepository = userProfileRepository;
  }

  @Override
  protected List<UserDetails> loadUsersByUsername(final String username) {
    return super.loadUsersByUsername(username).stream()
        .map(
            userDetails -> {
              final var profile = userProfileRepository.findByUsername(userDetails.getUsername());
              return (UserDetails)
                  new CamundaUserDetails(userDetails, profile.getId(), profile.getName());
            })
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
    final var user = super.loadUserByUsername(username);
    final var profile = userProfileRepository.findByUsername(username);
    return new CamundaUserDetails(user, profile.getId(), profile.getName());
  }

  private List<GrantedAuthority> loadPermissionsOfRoles(final List<GrantedAuthority> roles) {
    final List<GrantedAuthority> permissions = new ArrayList<>();

    for (final GrantedAuthority role : roles) {
      final List<String> permissionsForRole =
          getJdbcTemplate()
              .queryForList(
                  "select rp.permission "
                      + "from role_permissions rp "
                      + "where rp.role_authority = ?",
                  String.class,
                  role.getAuthority().replace("ROLE_", ""));

      permissions.addAll(permissionsForRole.stream().map(SimpleGrantedAuthority::new).toList());
    }

    return permissions.stream().distinct().toList();
  }
}
