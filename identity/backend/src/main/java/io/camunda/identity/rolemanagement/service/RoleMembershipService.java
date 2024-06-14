/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import io.camunda.identity.rolemanagement.repository.RoleRepository;
import io.camunda.identity.security.CamundaUserDetails;
import io.camunda.identity.security.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.service.GroupService;
import io.camunda.identity.usermanagement.service.UserService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleMembershipService {

  private final UserService userService;
  private final GroupService groupService;
  private final CamundaUserDetailsManager camundaUserDetailsManager;
  private final PasswordEncoder passwordEncoder;
  private final RoleRepository roleRepository;

  public RoleMembershipService(
      final UserService userService,
      final GroupService groupService,
      final CamundaUserDetailsManager camundaUserDetailsManager,
      final PasswordEncoder passwordEncoder,
      final RoleRepository roleRepository) {
    this.userService = userService;
    this.groupService = groupService;
    this.camundaUserDetailsManager = camundaUserDetailsManager;
    this.passwordEncoder = passwordEncoder;
    this.roleRepository = roleRepository;
  }

  public List<String> getRolesOfUserByUserId(final long userId) {
    final CamundaUserDetails camundaUserDetails =
        camundaUserDetailsManager.loadUserByUsername(
            userService.findUserById(userId).getUsername());

    return camundaUserDetails.getRoles().stream()
        .map(GrantedAuthority::getAuthority)
        .map(roleName -> roleName.replace("ROLE_", ""))
        .toList();
  }

  public void assignRoleToUser(final String roleName, final long userId) {
    if (!roleRepository.existsById(roleName)) {
      throw new RuntimeException("role.notFound");
    }

    final CamundaUserDetails camundaUserDetails =
        camundaUserDetailsManager.loadUserByUsername(
            userService.findUserById(userId).getUsername());

    final List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.addAll(camundaUserDetails.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

    saveUserDetailsWithAuthorities(camundaUserDetails, authorities);
  }

  public void unassignRoleFromUser(final String roleName, final long userId) {
    if (!roleRepository.existsById(roleName)) {
      throw new RuntimeException("role.notFound");
    }

    final CamundaUserDetails camundaUserDetails =
        camundaUserDetailsManager.loadUserByUsername(
            userService.findUserById(userId).getUsername());

    final List<GrantedAuthority> authorities = new ArrayList<>();
    final String prefixedRoleName = "ROLE_" + roleName;
    authorities.addAll(
        camundaUserDetails.getAuthorities().stream()
            .filter(grantedAuthority -> !prefixedRoleName.equals(grantedAuthority.getAuthority()))
            .toList());

    saveUserDetailsWithAuthorities(camundaUserDetails, authorities);
  }

  private void saveUserDetailsWithAuthorities(
      final CamundaUserDetails camundaUserDetails, final List<GrantedAuthority> authorities) {

    final UserDetails userDetails =
        User.withUsername(camundaUserDetails.getUsername())
            .password(camundaUserDetails.getPassword())
            .passwordEncoder(passwordEncoder::encode)
            .authorities(authorities)
            .disabled(!camundaUserDetails.isEnabled())
            .build();

    camundaUserDetailsManager.updateUser(userDetails);
  }

  public List<String> getRolesOfGroupByGroupId(final Long groupId) {
    final CamundaGroup group = groupService.findGroupById(groupId);

    return camundaUserDetailsManager.findGroupAuthorities(group.name()).stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
  }

  public void assignRoleToGroup(final String roleName, final long groupId) {
    if (!roleRepository.existsById(roleName)) {
      throw new RuntimeException("role.notFound");
    }
    final CamundaGroup group = groupService.findGroupById(groupId);

    camundaUserDetailsManager.addGroupAuthority(
        group.name(), new SimpleGrantedAuthority("ROLE_" + roleName));
  }

  public void unassignRoleFromGroup(final String roleName, final long groupId) {
    if (!roleRepository.existsById(roleName)) {
      throw new RuntimeException("role.notFound");
    }
    final CamundaGroup group = groupService.findGroupById(groupId);

    camundaUserDetailsManager.removeGroupAuthority(
        group.name(), new SimpleGrantedAuthority("ROLE_" + roleName));
  }
}
