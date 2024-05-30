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
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

  public Group findGroupByName(final String groupName) {
    final Group group = groupRepository.findGroupByName(groupName);
    if (group.id() == null) {
      throw new RuntimeException("group.notFound");
    }
    return group;
  }

  public Group findGroupById(final Integer groupId) {
    final Group group = groupRepository.findGroupById(groupId);
    if (group.id() == null) {
      throw new RuntimeException("group.notFound");
    }
    return group;
  }

  public Group createGroup(final Group group) {
    userDetailsManager.createGroup(group.name(), Collections.emptyList());
    return groupRepository.findGroupByName(group.name());
  }

  public void deleteGroup(final Group group) {
    userDetailsManager.deleteGroup(group.name());
  }

  public void deleteGroupById(final Integer groupId) {
    groupRepository.deleteGroupById(groupId);
  }

  public Group renameGroup(final String oldName, final Group group) {
    userDetailsManager.renameGroup(oldName, group.name());
    return findGroupByName(group.name());
  }

  public Group renameGroupById(final Integer groupId, final Group group) {
    if (group.id() == null || !Objects.equals(groupId, group.id())) {
      throw new RuntimeException("group.notFound");
    }
    if (!StringUtils.hasText(group.name())) {
      throw new RuntimeException("group.name.empty");
    }
    final Group existingGroup = findGroupById(groupId);
    return renameGroup(existingGroup.name(), group);
  }
}
