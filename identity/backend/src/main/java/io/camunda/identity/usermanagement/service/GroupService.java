/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.repository.GroupRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {
  private final CamundaUserDetailsManager userDetailsManager;

  private final GroupRepository groupRepository;

  public GroupService(
      final CamundaUserDetailsManager userDetailsManager, final GroupRepository groupRepository) {
    this.userDetailsManager = userDetailsManager;
    this.groupRepository = groupRepository;
  }

  public List<CamundaGroup> findAllGroups() {
    return groupRepository.findAll().stream()
        .map(group -> new CamundaGroup(group.getId(), group.getName()))
        .toList();
  }

  public CamundaGroup findGroupByName(final String groupName) {
    final var group =
        groupRepository
            .findByName(groupName)
            .orElseThrow(() -> new RuntimeException("group.notFound"));
    return new CamundaGroup(group.getId(), group.getName());
  }

  public CamundaGroup findGroupById(final Long groupId) {
    final var group =
        groupRepository.findById(groupId).orElseThrow(() -> new RuntimeException("group.notFound"));
    return new CamundaGroup(group.getId(), group.getName());
  }

  public CamundaGroup createGroup(final CamundaGroup group) {
    userDetailsManager.createGroup(group.name(), Collections.emptyList());
    return findGroupByName(group.name());
  }

  public void deleteGroup(final CamundaGroup group) {
    userDetailsManager.deleteGroup(group.name());
  }

  public void deleteGroupById(final Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new RuntimeException("group.notFound");
    }
    groupRepository.deleteById(groupId);
  }

  public CamundaGroup renameGroup(final String name, final CamundaGroup group) {
    userDetailsManager.renameGroup(name, group.name());
    return findGroupByName(group.name());
  }

  public CamundaGroup renameGroupById(final Long groupId, final CamundaGroup updatedGroup) {
    final CamundaGroup group = findGroupById(groupId);
    userDetailsManager.renameGroup(group.name(), updatedGroup.name());
    return new CamundaGroup(groupId, updatedGroup.name());
  }
}
