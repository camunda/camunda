/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.user.Group;
import io.camunda.identity.usermanagement.repository.GroupRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GroupService {
  private final CamundaUserDetailsManager userDetailsManager;

  private final GroupRepository groupRepository;

  public GroupService(
      final CamundaUserDetailsManager userDetailsManager, final GroupRepository groupRepository) {
    this.userDetailsManager = userDetailsManager;
    this.groupRepository = groupRepository;
  }

  public List<Group> findAllGroups() {
    return groupRepository.findAllGroups();
  }

  public Group findGroupByName(final String group) {
    return groupRepository.findGroup(group);
  }

  public Group createGroup(final Group group) {
    userDetailsManager.createGroup(group.name(), Collections.emptyList());
    return groupRepository.findGroup(group.name());
  }

  public void deleteGroup(final Group group) {
    userDetailsManager.deleteGroup(group.name());
  }

  public Group updateGroup(final String name, final Group group) {
    userDetailsManager.renameGroup(name, group.name());
    return groupRepository.findGroup(group.name());
  }
}
