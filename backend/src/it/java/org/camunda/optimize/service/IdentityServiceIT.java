/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class IdentityServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtension = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtension = new EmbeddedOptimizeExtensionRule();

  private IdentityService identityService;

  @Before
  public void setup() {
    identityService = embeddedOptimizeExtension.getIdentityService();
  }

  @Test
  public void getUserByIdFromCache() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    identityService.addIdentity(userIdentity);
    identityService.addIdentity(new UserDto("otherId", "Frodo", "Baggins", "frodo.baggins@camunda.com"));

    final Optional<UserDto> retrievedUserDto = identityService.getUserById("testUser");
    assertThat(retrievedUserDto.isPresent(), is(true));
    assertThat(retrievedUserDto.get(), is(userIdentity));
  }

  @Test
  public void getGroupByIdFromCache() {
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits");
    identityService.addIdentity(groupIdentity);
    identityService.addIdentity(new GroupDto("orcs", "The Orcs"));

    final Optional<GroupDto> retrievedUserDto = identityService.getGroupById("hobbits");
    assertThat(retrievedUserDto.isPresent(), is(true));
    assertThat(retrievedUserDto.get(), is(groupIdentity));
  }
}
