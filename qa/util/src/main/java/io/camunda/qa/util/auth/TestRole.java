/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.util.List;

public record TestRole(
    String id, String name, List<Permissions> permissions, List<Membership> memberships) {

  public TestRole(final String roleId, final String roleName) {
    this(roleId, roleName, List.of(), List.of());
  }

  public static TestRole withoutMemberships(
      final String roleId, final String roleName, final List<Permissions> permissions) {
    return new TestRole(roleId, roleName, permissions, List.of());
  }

  public static TestRole withoutPermissions(
      final String roleId, final String roleName, final List<Membership> memberships) {
    return new TestRole(roleId, roleName, List.of(), memberships);
  }
}
