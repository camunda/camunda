/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.initializer;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.config.IdentityPresets;
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
public class DefaultUserInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultUserInitializer.class);
  private final CamundaUserDetailsManager userDetailsManager;
  private final PasswordEncoder passwordEncoder;
  private final IdentityPresets identityPresets;

  public DefaultUserInitializer(
      final DataSource dataSource,
      final PasswordEncoder passwordEncoder,
      final IdentityPresets identityPresets) {
    userDetailsManager = new CamundaUserDetailsManager(dataSource);
    this.passwordEncoder = passwordEncoder;
    this.identityPresets = identityPresets;
  }

  @PostConstruct
  public void setupUsers() {
    if (userDetailsManager.userExists(identityPresets.getUser())) {
      LOG.info("User '{}' already exists, skipping creation.", identityPresets.getUser());
      return;
    }

    final UserDetails user =
        User.builder()
            .username(identityPresets.getUser())
            .password(identityPresets.getPassword())
            .passwordEncoder(passwordEncoder::encode)
            .roles("DEFAULT_USER")
            .build();

    userDetailsManager.createUser(user);
  }
}
