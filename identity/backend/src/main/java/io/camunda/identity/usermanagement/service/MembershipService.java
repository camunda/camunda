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
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.repository.MembershipRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MembershipService {
  private final CamundaUserDetailsManager userDetailsManager;

  private final UserService userService;
  private final GroupService groupService;
  private final MembershipRepository membershipRepository;

  public MembershipService(
      final CamundaUserDetailsManager userDetailsManager,
      final UserService userService,
      final GroupService groupService,
      final MembershipRepository membershipRepository) {
    this.userDetailsManager = userDetailsManager;
    this.userService = userService;
    this.groupService = groupService;
    this.membershipRepository = membershipRepository;
  }

  public void addUserToGroup(final CamundaUser user, final CamundaGroup group) {
    userDetailsManager.addUserToGroup(user.getUsername(), group.name());
  }

  public void addUserToGroupByIds(final Long userId, final Long groupId) {
    final CamundaUser user = userService.findUserById(userId);
    final CamundaGroup group = groupService.findGroupById(groupId);
    addUserToGroup(user, group);
  }

  public void removeUserFromGroup(final CamundaUser user, final CamundaGroup group) {
    userDetailsManager.removeUserFromGroup(user.getUsername(), group.name());
  }

  public void removeUserFromGroupByIds(final Long userId, final Long groupId) {
    final CamundaUser user = userService.findUserById(userId);
    final CamundaGroup group = groupService.findGroupById(groupId);
    removeUserFromGroup(user, group);
  }

  public List<CamundaUser> getUsersOfGroup(final CamundaGroup group) {
    return userService.findUsersByUsernameIn(userDetailsManager.findUsersInGroup(group.name()));
  }

  public List<CamundaUser> getUsersOfGroupById(final Long groupId) {
    final CamundaGroup group = groupService.findGroupById(groupId);
    return getUsersOfGroup(group);
  }

  public List<CamundaGroup> getUserGroups(final CamundaUser user) {
    return membershipRepository.loadUserGroups(user.getUsername());
  }
}
