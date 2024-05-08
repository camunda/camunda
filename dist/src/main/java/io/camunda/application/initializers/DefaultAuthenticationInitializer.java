/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import io.camunda.application.Profile;
import io.camunda.operate.OperateProfileService;
import java.util.Set;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Adds the "auth" if none of the {@link OperateProfileService#AUTH_PROFILES} is set as an active
 * profile.
 */
public class DefaultAuthenticationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    final var activeProfiles = Set.of(env.getActiveProfiles());
    if (shouldApplyDefaultAuthenticationProfile(activeProfiles)) {
      env.addActiveProfile(OperateProfileService.DEFAULT_AUTH);
    }
  }

  protected boolean shouldApplyDefaultAuthenticationProfile(final Set<String> activeProfiles) {
    if (activeProfiles.contains(Profile.OPERATE.getId()) || activeProfiles.contains(Profile.TASKLIST.getId())) {
      return OperateProfileService.AUTH_PROFILES.stream().noneMatch(activeProfiles::contains);
    } else {
      return false;
    }
  }
}
