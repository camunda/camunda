/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.TestHelper;
import io.camunda.identity.rolemanagement.model.Role;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.apache.maven.surefire.shared.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringBootTest
public class RoleServiceTest {

  @Autowired private RoleService roleService;

  @Test
  public void createRoleWorks() {
    final Role role = new Role();
    role.setName("ADMIN");
    role.setDescription("Administrator role");
    role.setPermissions(new HashSet<>());

    final Role result = roleService.createRole(role);
    assertEquals("ADMIN", result.getName());
    assertEquals("Administrator role", result.getDescription());
    assertEquals(Collections.emptySet(), result.getPermissions());
  }

  @Test
  public void createRoleNameIsNullThrowsException() {
    final Role role = new Role();
    role.setName(null);

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("createRole.role.name: role.notValid", exception.getMessage());
  }

  @Test
  public void createRoleNameIsEmptyThrowsException() {
    final Role role = new Role();
    role.setName("");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("createRole.role.name: role.notValid", exception.getMessage());
  }

  @Test
  public void createRoleNameIsBlankThrowsException() {
    final Role role = new Role();
    role.setName(" ");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("createRole.role.name: role.notValid", exception.getMessage());
  }

  @Test
  public void createRoleNameIsWhitespaceThrowsException() {
    final Role role = new Role();
    role.setName("   ");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("createRole.role.name: role.notValid", exception.getMessage());
  }

  @Test
  public void deleteRoleWorks() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    List<Role> allRoles = roleService.findAllRoles();
    assertEquals(1, allRoles.size());

    roleService.deleteRoleByName(role.getName());

    allRoles = roleService.findAllRoles();

    assertEquals(0, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(role.getName())));
  }

  @Test
  public void deleteNonExistingRolePassesWithoutChanges() {
    final String randomString = RandomStringUtils.randomAlphabetic(2);

    List<Role> allRoles = roleService.findAllRoles();
    assertEquals(0, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(randomString)));

    roleService.deleteRoleByName(randomString);

    allRoles = roleService.findAllRoles();
    assertEquals(0, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(randomString)));
  }

  @Test
  public void findRoleByNameWorks() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    final Role roleByName = roleService.findRoleByName(role.getName());

    assertEquals(role.getName(), roleByName.getName());
    assertEquals(role.getDescription(), roleByName.getDescription());
  }

  @Test
  public void findRoleByNameRoleDoesNotExistThrowsException() {
    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> roleService.findRoleByName(RandomStringUtils.randomAlphabetic(2)));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  public void updateRoleWorks() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    final Role newRole = new Role();
    newRole.setName(role.getName());
    newRole.setDescription(RandomStringUtils.randomAlphabetic(15));

    final Role updatedRole = roleService.updateRole(role.getName(), newRole);

    assertEquals(newRole.getName(), updatedRole.getName());
    assertEquals(newRole.getDescription(), updatedRole.getDescription());
  }

  @Test
  public void updateRoleNameIsNullOrEmptyThrowsException() {
    final Role invalidRole = new Role();
    invalidRole.setName(null);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("validName", invalidRole));

    assertEquals("updateRole.role.name: role.notValid", exception.getMessage());

    invalidRole.setName("   ");

    exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("validName", invalidRole));

    assertEquals("updateRole.role.name: role.notValid", exception.getMessage());
  }

  @Test
  public void updateRoleNamesDontMatchThrowsException() {
    final Role validRole = new Role();
    validRole.setName("validName");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("differentValidName", validRole));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  public void updateRolePermissionsIsNullThrowsException() {
    final String name = RandomStringUtils.randomAlphabetic(10);
    final Role role = new Role();
    role.setName(name);
    role.setPermissions(null);

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.updateRole(name, role));

    assertEquals("updateRole.role.permissions: role.notValid", exception.getMessage());
  }
}
