/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.application.Profile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/*Post processor to enable separate migration profiles when `migration` profile is enabled.*/
public class MigrationProfilePostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    if (environment.acceptsProfiles(Profiles.of(Profile.MIGRATION.getId()))) {
      environment.addActiveProfile(Profile.PROCESS_MIGRATION.getId());
      environment.addActiveProfile(Profile.IDENTITY_MIGRATION.getId());
    }
  }
}
