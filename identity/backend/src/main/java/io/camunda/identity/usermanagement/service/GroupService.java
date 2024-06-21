/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.security.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.model.Group;
import io.camunda.identity.usermanagement.repository.GroupRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {
  private final CamundaUserDetailsManager camundaUserDetailsManager;

  private final GroupRepository groupRepository;

  public GroupService(
      final CamundaUserDetailsManager camundaUserDetailsManager,
      final GroupRepository groupRepository) {
    this.camundaUserDetailsManager = camundaUserDetailsManager;
    this.groupRepository = groupRepository;
  }

  public List<CamundaGroup> findAllGroups() {
    return groupRepository.findAll().stream()
        .map(group -> new CamundaGroup(group.getId(), group.getName()))
        .toList();
  }

  public CamundaGroup findGroupByName(final String groupName) {
    final Group group =
        groupRepository
            .findByName(groupName)
            .orElseThrow(() -> new RuntimeException("group.notFound"));
    return new CamundaGroup(group.getId(), group.getName());
  }

  public CamundaGroup findGroupById(final Long groupId) {
    final Group group = findById(groupId);
    return new CamundaGroup(group.getId(), group.getName());
  }

  public CamundaGroup createGroup(final CamundaGroup group) {
    camundaUserDetailsManager.createGroup(group.name(), Collections.emptyList());
    return findGroupByName(group.name());
  }

  public void deleteGroup(final CamundaGroup group) {
    camundaUserDetailsManager.deleteGroup(group.name());
  }

  public void deleteGroupById(final Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new RuntimeException("group.notFound");
    }
    groupRepository.deleteById(groupId);
  }

  public CamundaGroup updateGroup(final Long groupId, final CamundaGroup updatedGroup) {
    if (groupId == null || !groupId.equals(updatedGroup.id())) {
      throw new RuntimeException("group.notFound");
    }

    Group group = findById(groupId);
    group.setName(updatedGroup.name());
    group = groupRepository.save(group);

    return new CamundaGroup(groupId, group.getName());
  }

  private Group findById(final Long groupId) {
    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new RuntimeException("group.notFound"));
  }

  public void assignRoleToGroup(final Long groupId, final String roleName) {
    final Group group = groupRepository.findById(groupId).orElseThrow();
    camundaUserDetailsManager.addGroupAuthority(
        group.getName(), new SimpleGrantedAuthority(roleName));
  }

  public void unassignRoleFromGroup(final Long groupId, final String roleName) {
    final Group group = groupRepository.findById(groupId).orElseThrow();
    camundaUserDetailsManager.removeGroupAuthority(
        group.getName(), new SimpleGrantedAuthority(roleName));
  }
}
