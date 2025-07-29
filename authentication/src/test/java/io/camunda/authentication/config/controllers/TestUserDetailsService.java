/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class TestUserDetailsService implements UserDetailsService {

  public static final String DEMO_USERNAME = "demo";

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    if (!DEMO_USERNAME.equals(username)) {
      throw new UsernameNotFoundException(
          "This service only manages the demo user; "
              + "make this more flexible if you need it for your tests");
    }
    return User.withUsername(username).password(DEMO_USERNAME).build();
  }
}
