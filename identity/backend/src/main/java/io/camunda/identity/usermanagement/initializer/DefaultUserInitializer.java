/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.initializer;

import io.camunda.identity.config.IdentityConfiguration;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auth-basic")
@DependsOnDatabaseInitialization
public class DefaultUserInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultUserInitializer.class);
  private final UserService userService;
  private final IdentityConfiguration identityConfiguration;

  public DefaultUserInitializer(
      final UserService userService, final IdentityConfiguration identityConfiguration) {
    this.userService = userService;
    this.identityConfiguration = identityConfiguration;
  }

  @PostConstruct
  public void setupUsers() {
    identityConfiguration.getUsers().forEach(this::setupUser);
  }

  public void setupUser(final CamundaUserWithPassword camundaUserWithPassword) {
    try {
      userService.createUser(camundaUserWithPassword);
    } catch (final Exception e) {
      if ("user.duplicate".equals(e.getMessage())) {
        LOG.info("User '{}' already exists, updating it.", camundaUserWithPassword.getUsername());
        return;
      }
      throw e;
    }
  }
}
