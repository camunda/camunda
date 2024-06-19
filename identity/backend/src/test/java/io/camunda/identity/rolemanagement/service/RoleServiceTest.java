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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.TestHelper;
import io.camunda.identity.permissions.PermissionEnum;
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
  public void createRole_Works() {
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
  public void createRole_NameIsNull_ThrowsException() {
    final Role role = new Role();
    role.setName(null);

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsEmpty_ThrowsException() {
    final Role role = new Role();
    role.setName("");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsBlank_ThrowsException() {
    final Role role = new Role();
    role.setName(" ");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsWhitespace_ThrowsException() {
    final Role role = new Role();
    role.setName("   ");

    final RuntimeException exception =
        assertThrows(RuntimeException.class, () -> roleService.createRole(role));

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void deleteRole_Works() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    roleService.deleteRoleByName(role.getName());

    final List<Role> allRoles = roleService.findAllRoles();

    assertEquals(2, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(role.getName())));
  }

  @Test
  public void deleteNonExistingRole_PassesWithoutChanges() {
    final String randomString = RandomStringUtils.randomAlphabetic(2);

    List<Role> allRoles = roleService.findAllRoles();
    assertEquals(2, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(randomString)));

    roleService.deleteRoleByName(randomString);

    allRoles = roleService.findAllRoles();
    assertEquals(2, allRoles.size());
    assertFalse(allRoles.stream().anyMatch(r -> r.getName().equals(randomString)));
  }

  @Test
  public void findRoleByName_Works() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    final Role roleByName = roleService.findRoleByName(role.getName());

    assertEquals(role.getName(), roleByName.getName());
    assertEquals(role.getDescription(), roleByName.getDescription());
  }

  @Test
  public void findRoleByName_RoleDoesNotExist_ThrowsException() {
    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> roleService.findRoleByName(RandomStringUtils.randomAlphabetic(2)));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  public void updateRole_Works() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    final Role newRole = new Role();
    newRole.setName(role.getName());
    newRole.setDescription(RandomStringUtils.randomAlphabetic(15));

    final Role updatedRole = roleService.updateRole(role.getName(), newRole);

    assertEquals(newRole.getName(), updatedRole.getName());
    assertEquals(newRole.getDescription(), updatedRole.getDescription());
  }

  @Test
  public void updateRole_NameIsNullOrEmpty_ThrowsException() {
    final Role invalidRole = new Role();
    invalidRole.setName(null);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("validName", invalidRole));

    assertEquals("role.notValid", exception.getMessage());

    invalidRole.setName("   ");

    exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("validName", invalidRole));

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void updateRole_NamesDontMatch_ThrowsException() {
    final Role validRole = new Role();
    validRole.setName("validName");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> roleService.updateRole("differentValidName", validRole));

    assertEquals("role.notFound", exception.getMessage());
  }

  @Test
  public void permissionsOfRole_Works() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    assertTrue(role.getPermissions().isEmpty());

    roleService.assignPermissionToRole(role.getName(), "READ_ALL");
    Role updatedRole = roleService.findRoleByName(role.getName());

    assertEquals(1, updatedRole.getPermissions().size());
    assertTrue(updatedRole.getPermissions().contains(PermissionEnum.READ_ALL));

    roleService.assignPermissionToRole(role.getName(), "CREATE_ALL");
    updatedRole = roleService.findRoleByName(role.getName());

    assertEquals(2, updatedRole.getPermissions().size());
    assertTrue(updatedRole.getPermissions().contains(PermissionEnum.READ_ALL));
    assertTrue(updatedRole.getPermissions().contains(PermissionEnum.CREATE_ALL));

    final List<PermissionEnum> allPermissionsOfRole =
        roleService.findAllPermissionsOfRole(role.getName());

    assertEquals(2, allPermissionsOfRole.size());
    assertTrue(allPermissionsOfRole.contains(PermissionEnum.READ_ALL));
    assertTrue(allPermissionsOfRole.contains(PermissionEnum.CREATE_ALL));

    roleService.unassignPermissionFromRole(role.getName(), "READ_ALL");
    updatedRole = roleService.findRoleByName(role.getName());

    assertEquals(1, updatedRole.getPermissions().size());
    assertTrue(updatedRole.getPermissions().contains(PermissionEnum.CREATE_ALL));
    assertFalse(updatedRole.getPermissions().contains(PermissionEnum.READ_ALL));
  }

  @Test
  public void unknownPermissionsForRoles_ThrowsException() {
    final Role role = TestHelper.createAndSaveRandomRole(roleService);

    assertTrue(role.getPermissions().isEmpty());

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> roleService.assignPermissionToRole(role.getName(), "UNKNOWN_PERMISSION"));

    assertEquals("permission.notFound", exception.getMessage());

    exception =
        assertThrows(
            RuntimeException.class,
            () -> roleService.unassignPermissionFromRole(role.getName(), "UNKNOWN_PERMISSION"));

    assertEquals("permission.notFound", exception.getMessage());
  }
}
