/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.util.List;

public record TestGroup(
    String id, String name, List<Permissions> permissions, List<Membership> memberships) {

  public TestGroup(final String groupId, final String groupName) {
    this(groupId, groupName, List.of(), List.of());
  }

  public static TestGroup withoutMemberships(
      final String groupId, final String groupName, final List<Permissions> permissions) {
    return new TestGroup(groupId, groupName, permissions, List.of());
  }

  public static TestGroup withoutPermissions(
      final String groupId, final String groupName, final List<Membership> memberships) {
    return new TestGroup(groupId, groupName, List.of(), memberships);
  }
}
