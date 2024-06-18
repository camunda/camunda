/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.rolemanagement.model.Role;
import java.util.Collections;
import java.util.HashSet;
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
        assertThrows(
            RuntimeException.class,
            () -> {
              roleService.createRole(role);
            });

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsEmpty_ThrowsException() {
    final Role role = new Role();
    role.setName("");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              roleService.createRole(role);
            });

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsBlank_ThrowsException() {
    final Role role = new Role();
    role.setName(" ");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              roleService.createRole(role);
            });

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void createRole_NameIsWhitespace_ThrowsException() {
    final Role role = new Role();
    role.setName("   ");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              roleService.createRole(role);
            });

    assertEquals("role.notValid", exception.getMessage());
  }

  @Test
  public void deleteRole_Works() {}
}
