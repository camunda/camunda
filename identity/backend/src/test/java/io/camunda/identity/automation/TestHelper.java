/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.automation;

import io.camunda.identity.automation.rolemanagement.model.Role;
import io.camunda.identity.automation.rolemanagement.service.RoleService;
import io.camunda.identity.automation.usermanagement.CamundaGroup;
import io.camunda.identity.automation.usermanagement.CamundaUser;
import io.camunda.identity.automation.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.automation.usermanagement.service.GroupService;
import io.camunda.identity.automation.usermanagement.service.UserService;
import org.apache.maven.surefire.shared.lang3.RandomStringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class TestHelper {

  public static final String DEFAULT_USER_ROLE = "DEFAULT_USER";

  public static CamundaUser createAndSaveRandomUser(final UserService userService) {
    return userService.createUser(
        new CamundaUserWithPassword(
            RandomStringUtils.randomAlphabetic(10), "email", false, "password"));
  }

  public static CamundaGroup createAndSaveRandomGroup(final GroupService groupService) {
    return groupService.createGroup(new CamundaGroup(RandomStringUtils.randomAlphabetic(10)));
  }

  public static Role createAndSaveRandomRole(final RoleService roleService) {
    final String roleName = RandomStringUtils.randomAlphabetic(10);
    final String description = RandomStringUtils.randomAlphabetic(15);

    final Role role = new Role();
    role.setName(roleName);
    role.setDescription(description);

    return roleService.createRole(role);
  }

  public static String buildPrefixedRoleName(final String roleName) {
    return String.format("ROLE_%s", roleName);
  }

  public static SimpleGrantedAuthority buildSimpleGrantedAuthorityFromRoleName(
      final String roleName) {
    return new SimpleGrantedAuthority(buildPrefixedRoleName(roleName));
  }
}
