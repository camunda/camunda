/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MapPropertySource;

public class ProfilesStreamlining implements EnvironmentPostProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProfilesStreamlining.class);

  private static final Set<String> MERGING_PROFILES = Set.of(
      Profile.OPERATE.getId(),
      Profile.TASKLIST.getId(),
      Profile.IDENTITY.getId(),
      Profile.GATEWAY.getId()
  );

  public boolean isSkipProcessor() {
    return Boolean.parseBoolean(System.getenv("SKIP_PROCESSOR"));
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment,
      final SpringApplication application) {
    if (isSkipProcessor()) {
      LOGGER.info("\uD83D\uDFE7 Skipping profiles streamlining processor");
      return;
    }

    LOGGER.info("🚀 Streamlining profiles...");

    final Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
    boolean gatewayActive = activeProfiles.contains(Profile.GATEWAY.getId());

    final Set<String> intersection = Sets.intersection(activeProfiles, MERGING_PROFILES);
    if (intersection.isEmpty()) {
      return;
    }

    final Set<String> profilesToActivate = Sets.difference(MERGING_PROFILES, intersection);
    for (String profileToActivate : profilesToActivate) {
      LOGGER.info("🚀 Activating profile {}", profileToActivate);
      gatewayActive = gatewayActive || profileToActivate.equals(Profile.GATEWAY.getId());
      environment.addActiveProfile(profileToActivate);
    }

    if (gatewayActive) {
      LOGGER.info("🚀 Gateway active. Deactivating broker's embedded gateway...");
      environment.getPropertySources().addFirst(new MapPropertySource(
          this.getClass().getSimpleName(),Map.of(
              "zeebe.broker.gateway.enable", "false",
              "camunda.webapps.enabled", "true"
      )));
    }
  }
}
