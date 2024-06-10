/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.initializer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.config.IdentityPresets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

@CamundaSpringBootTest(
    properties = {
      "camunda.presets.identity.user=" + DefaultUserInitializerTest.USERNAME,
      "camunda.presets.identity.password=" + DefaultUserInitializerTest.PASSWORD
    },
    profiles = {"test", "auth-basic"})
public class DefaultUserInitializerTest {

  static final String USERNAME = "test";
  static final String PASSWORD = "password";

  @Autowired DefaultUserInitializer defaultUserInitializer;
  @Autowired IdentityPresets presets;
  @Autowired UserDetailsManager camundaUserDetailsManager;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void defaultUserIsInitialized() {

    final var defaultUser = camundaUserDetailsManager.loadUserByUsername(USERNAME);
    assertTrue(passwordEncoder.matches("password", defaultUser.getPassword()));
  }

  @Test
  void defaultUserIsInitializedOnce() {
    presets.setPassword("new-password");

    defaultUserInitializer.setupUsers();

    final var defaultUser = camundaUserDetailsManager.loadUserByUsername(USERNAME);
    assertFalse(passwordEncoder.matches("new-password", defaultUser.getPassword()));
    assertTrue(passwordEncoder.matches(PASSWORD, defaultUser.getPassword()));
  }
}
