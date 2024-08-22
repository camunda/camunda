/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.UserServices;
import io.camunda.service.query.filter.UserSearchQueryStub;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.userdetails.UserDetails;

public class CamundaUserDetailsServiceTest {

  private static final String TEST_USER_ID = "username1";

  private UserServices userService;
  private CamundaUserDetailsService userDetailsService;

  @Before
  public void setup() {
    final StubbedCamundaSearchClient client = new StubbedCamundaSearchClient();
    new UserSearchQueryStub().registerWith(client);
    userService = new UserServices<>(new StubbedBrokerClient(), client);
    userDetailsService = new CamundaUserDetailsService(userService);
  }

  @Test
  public void testCustomUserIsAdded() {
    // then
    final UserDetails user = userDetailsService.loadUserByUsername(TEST_USER_ID);
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(user.getPassword()).isEqualTo("password1");
  }
}
