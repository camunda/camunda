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
}
