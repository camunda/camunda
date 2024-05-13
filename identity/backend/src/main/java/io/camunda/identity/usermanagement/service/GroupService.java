/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.usermanagement.Group;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GroupService {
  private final CamundaUserDetailsManager userDetailsManager;

  public GroupService(final CamundaUserDetailsManager userDetailsManager) {
    this.userDetailsManager = userDetailsManager;
  }

  public List<Group> findAllGroups() {
    return userDetailsManager.findAllGroups().stream()
        .map(g -> new Group(userDetailsManager.findGroupId(g), g))
        .toList();
  }

  public Optional<Group> findGroupByName(final String group) {
    final int groupId = userDetailsManager.findGroupId(group);
    if (groupId < 0) {
      return Optional.empty();
    }
    return Optional.of(new Group(groupId, group));
  }

  public Group createGroup(final Group group) {
    userDetailsManager.createGroup(group.name(), Collections.emptyList());
    final int groupId = userDetailsManager.findGroupId(group.name());
    return new Group(groupId, group.name());
  }

  public void deleteGroup(final Group group) {
    userDetailsManager.deleteGroup(group.name());
  }

  public Group updateGroup(final String name, final Group group) {
    userDetailsManager.renameGroup(name, group.name());
    final int groupId = userDetailsManager.findGroupId(group.name());
    return new Group(groupId, group.name());
  }
}
