/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.initializer;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("auth-basic")
@DependsOnDatabaseInitialization
public class DemoUserInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(DemoUserInitializer.class);
  private final CamundaUserDetailsManager userDetailsManager;
  private final PasswordEncoder passwordEncoder;

  public DemoUserInitializer(final DataSource dataSource, final PasswordEncoder passwordEncoder) {
    userDetailsManager = new CamundaUserDetailsManager(dataSource);
    this.passwordEncoder = passwordEncoder;
  }

  @PostConstruct
  public void setupUsers() {
    if (userDetailsManager.userExists("demo")) {
      LOG.info("User 'demo' already exists, skipping creation.");
      return;
    }

    final UserDetails user =
        User.builder()
            .username("demo")
            .password("demo")
            .passwordEncoder(passwordEncoder::encode)
            .roles("DEFAULT_USER")
            .build();

    userDetailsManager.createUser(user);
  }
}
