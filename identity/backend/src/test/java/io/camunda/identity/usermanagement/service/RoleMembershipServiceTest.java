/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.rolemanagement.service.RoleMembershipService;
import io.camunda.identity.rolemanagement.service.RoleService;
import io.camunda.identity.security.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@CamundaSpringBootTest
public class RoleMembershipServiceTest {

  @Autowired UserService userService;
  @Autowired RoleService roleService;
  @Autowired RoleMembershipService roleMembershipService;
  @Autowired private CamundaUserDetailsManager camundaUserDetailsManager;

  @Test
  void assignRoleToUser() {
    final var username = "user" + UUID.randomUUID();
    final var password = "password";
    final var user =
        userService.createUser(new CamundaUserWithPassword(username, "email", false, password));

    final var role1 = new Role();
    role1.setName("R1");
    role1.setDescription("R1desc");
    roleService.createRole(role1);
    final var role2 = new Role();
    role2.setName("R2");
    role2.setDescription("R2desc");
    roleService.createRole(role2);

    roleMembershipService.assignRoleToUser("R1", user.getId());
    final var userDetail = camundaUserDetailsManager.loadUserByUsername(username);
    Assertions.assertTrue(
        userDetail.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_R1")));

    roleMembershipService.assignRoleToUser("R2", user.getId());

    final var userDetail2 = camundaUserDetailsManager.loadUserByUsername(username);
    Assertions.assertTrue(
        userDetail2.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_R1")));
    Assertions.assertTrue(
        userDetail2.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_R2")));
    Assertions.assertTrue(
        userDetail2.getAuthorities().contains(new SimpleGrantedAuthority("write:*")));
    Assertions.assertFalse(userDetail.getRoles().contains("write:*"));
  }
}
