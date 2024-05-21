/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.Group;
import io.camunda.identity.usermanagement.repository.MembershipRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {
  private final CamundaUserDetailsManager userDetailsManager;

  private final UserService userService;

  private final MembershipRepository membershipRepository;

  public MembershipService(
      final CamundaUserDetailsManager userDetailsManager,
      final UserService userService,
      final MembershipRepository membershipRepository) {
    this.userDetailsManager = userDetailsManager;
    this.userService = userService;
    this.membershipRepository = membershipRepository;
  }

  public void addUserToGroup(final CamundaUser user, final Group group) {
    userDetailsManager.addUserToGroup(user.username(), group.name());
  }

  public void removeUserFromGroup(final CamundaUser user, final Group group) {
    userDetailsManager.removeUserFromGroup(user.username(), group.name());
  }

  public List<CamundaUser> getMembers(final Group group) {
    return userDetailsManager.findUsersInGroup(group.name()).stream()
        .map(userService::findUserByUsername)
        .toList();
  }

  public List<Group> getUserGroups(final CamundaUser user) {
    return membershipRepository.loadUserGroups(user.username());
  }
}
