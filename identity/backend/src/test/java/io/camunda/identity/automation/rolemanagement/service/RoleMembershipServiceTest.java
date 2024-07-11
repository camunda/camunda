/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.automation.rolemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.automation.CamundaSpringBootTest;
import io.camunda.identity.automation.TestHelper;
import io.camunda.identity.automation.rolemanagement.model.Role;
import io.camunda.identity.automation.security.CamundaUserDetails;
import io.camunda.identity.automation.security.CamundaUserDetailsManager;
import io.camunda.identity.automation.usermanagement.CamundaGroup;
import io.camunda.identity.automation.usermanagement.CamundaUser;
import io.camunda.identity.automation.usermanagement.service.GroupService;
import io.camunda.identity.automation.usermanagement.service.UserService;
import java.util.List;
import org.apache.maven.surefire.shared.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

@CamundaSpringBootTest
class RoleMembershipServiceTest {

  @Autowired private UserService userService;
  @Autowired private RoleService roleService;
  @Autowired private GroupService groupService;
  @Autowired private RoleMembershipService roleMembershipService;
  @Autowired private CamundaUserDetailsManager camundaUserDetailsManager;

  @Test
  void assignRoleToUserWorks() {
    final CamundaUser user = TestHelper.createAndSaveRandomUser(userService);
    final Role role1 = TestHelper.createAndSaveRandomRole(roleService);
    final Role role2 = TestHelper.createAndSaveRandomRole(roleService);

    roleMembershipService.assignRoleToUser(role1.getName(), user.getId());
    final CamundaUserDetails userDetail =
        camundaUserDetailsManager.loadUserByUsername(user.getUsername());

    assertTrue(
        userDetail
            .getAuthorities()
            .contains(TestHelper.buildSimpleGrantedAuthorityFromRoleName(role1.getName())));

    roleMembershipService.assignRoleToUser(role2.getName(), user.getId());
    final CamundaUserDetails userDetail2 =
        camundaUserDetailsManager.loadUserByUsername(user.getUsername());

    assertTrue(
        userDetail2
            .getAuthorities()
            .contains(TestHelper.buildSimpleGrantedAuthorityFromRoleName(role1.getName())));
    assertTrue(
        userDetail2
            .getAuthorities()
            .contains(TestHelper.buildSimpleGrantedAuthorityFromRoleName(role2.getName())));
    assertEquals(3, userDetail2.getAuthorities().size());
  }

  @Test
  void assignRoleToUserThrowsExceptionIfRoleDoesNotExist() {
    final CamundaUser user = TestHelper.createAndSaveRandomUser(userService);

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                roleMembershipService.assignRoleToUser(
                    RandomStringUtils.randomAlphabetic(2), user.getId()));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  void getRolesByUserIdWorks() {
    final CamundaUser user = TestHelper.createAndSaveRandomUser(userService);
    final Role role1 = TestHelper.createAndSaveRandomRole(roleService);
    final Role role2 = TestHelper.createAndSaveRandomRole(roleService);
    roleMembershipService.assignRoleToUser(role1.getName(), user.getId());
    roleMembershipService.assignRoleToUser(role2.getName(), user.getId());

    final List<String> roles = roleMembershipService.getRolesByUserId(user.getId());

    assertEquals(3, roles.size());
    assertTrue(roles.contains(TestHelper.DEFAULT_USER_ROLE));
    assertTrue(roles.contains(role1.getName()));
    assertTrue(roles.contains(role2.getName()));
  }

  @Test
  void unassignRoleFromUserWorks() {
    final CamundaUser user = TestHelper.createAndSaveRandomUser(userService);
    final Role role = TestHelper.createAndSaveRandomRole(roleService);
    roleMembershipService.assignRoleToUser(role.getName(), user.getId());

    final List<String> roles = roleMembershipService.getRolesByUserId(user.getId());

    assertEquals(2, roles.size());
    assertTrue(roles.contains(TestHelper.DEFAULT_USER_ROLE));
    assertTrue(roles.contains(role.getName()));

    roleMembershipService.unassignRoleFromUser(role.getName(), user.getId());

    final CamundaUserDetails userDetail =
        camundaUserDetailsManager.loadUserByUsername(user.getUsername());

    assertEquals(1, userDetail.getAuthorities().size());
    assertTrue(
        userDetail
            .getAuthorities()
            .contains(
                TestHelper.buildSimpleGrantedAuthorityFromRoleName(TestHelper.DEFAULT_USER_ROLE)));
    assertFalse(
        userDetail
            .getAuthorities()
            .contains(TestHelper.buildSimpleGrantedAuthorityFromRoleName(role.getName())));
  }

  @Test
  void unassignRoleFromUserThrowsExceptionIfRoleDoesNotExist() {
    final CamundaUser user = TestHelper.createAndSaveRandomUser(userService);

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                roleMembershipService.unassignRoleFromUser(
                    RandomStringUtils.randomAlphabetic(2), user.getId()));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  void assignRoleToGroupWorks() {
    final CamundaGroup group = TestHelper.createAndSaveRandomGroup(groupService);
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    roleMembershipService.assignRoleToGroup(role.getName(), group.id());

    final List<String> roles =
        camundaUserDetailsManager.findGroupAuthorities(group.name()).stream()
            .map(GrantedAuthority::getAuthority)
            .map(authorityName -> authorityName.replace("ROLE_", ""))
            .toList();

    assertEquals(1, roles.size());
    assertTrue(roles.contains(role.getName()));
  }

  @Test
  void assignRoleToGroupThrowsExceptionIfRoleDoesNotExist() {
    final CamundaGroup group = TestHelper.createAndSaveRandomGroup(groupService);

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                roleMembershipService.assignRoleToGroup(
                    RandomStringUtils.randomAlphabetic(2), group.id()));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  void getRolesByGroupIdWorks() {
    final CamundaGroup group = TestHelper.createAndSaveRandomGroup(groupService);
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    roleMembershipService.assignRoleToGroup(role.getName(), group.id());

    final List<String> roles = roleMembershipService.getRolesByGroupId(group.id());

    assertEquals(1, roles.size());
    assertTrue(roles.contains(role.getName()));
  }

  @Test
  void unassignRoleFromGroupWorks() {
    final CamundaGroup group = TestHelper.createAndSaveRandomGroup(groupService);
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    roleMembershipService.assignRoleToGroup(role.getName(), group.id());
    roleMembershipService.unassignRoleFromGroup(role.getName(), group.id());

    final List<String> roles = roleMembershipService.getRolesByGroupId(group.id());

    assertEquals(0, roles.size());
  }

  @Test
  void unassignRoleFromGroupThrowsExceptionIfRoleDoesNotExist() {
    final CamundaGroup group = TestHelper.createAndSaveRandomGroup(groupService);

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                roleMembershipService.unassignRoleFromGroup(
                    RandomStringUtils.randomAlphabetic(2), group.id()));

    assertEquals("role.notFound", exception.getMessage());
  }
}
