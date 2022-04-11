/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformIdentityServiceIT extends AbstractIT {

  private PlatformIdentityService identityService;

  @BeforeEach
  public void setup() {
    identityService = embeddedOptimizeExtension.getIdentityService();
  }

  @Test
  public void getUserByIdFromCache() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    identityService.addIdentity(userIdentity);
    identityService.addIdentity(new UserDto("otherId", "Frodo", "Baggins", "frodo.baggins@camunda.com"));

    final Optional<UserDto> retrievedUserDto = identityService.getUserById("testUser");
    assertThat(retrievedUserDto).isPresent().get().isEqualTo(userIdentity);
  }

  @Test
  public void getGroupByIdFromCache() {
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
    identityService.addIdentity(groupIdentity);
    identityService.addIdentity(new GroupDto("orcs", "The Orcs", 1000L));

    final Optional<GroupDto> retrievedUserDto = identityService.getGroupById("hobbits");
    assertThat(retrievedUserDto).isPresent().get().isEqualTo(groupIdentity);
  }
}
