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

public class ProfilesStreamlining implements EnvironmentPostProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProfilesStreamlining.class);

  private static final Set<String> LEGACY_PROFILES = Set.of(
      Profile.OPERATE.getId(),
      Profile.TASKLIST.getId(),
      Profile.IDENTITY.getId()
  );

  private static final Set<String> MODERN_PROFILES = Set.of(
      Profile.BROKER.getId(),
      Profile.GATEWAY.getId()
  );

  private static final Map<String, Set<String>> IMPLIED_MODERN_PROFILES = Map.of(
    Profile.IDENTITY.getId(), Set.of(Profile.GATEWAY.getId()),
    Profile.TASKLIST.getId(), Set.of(Profile.GATEWAY.getId()),
    Profile.OPERATE.getId(), Set.of(Profile.GATEWAY.getId())
  );

  private static final Map<String, Set<String>> IMPLIED_LEGACY_PROFILES = Map.of(
      Profile.GATEWAY.getId(), Set.of(
          Profile.OPERATE.getId(),
          Profile.TASKLIST.getId(),
          Profile.IDENTITY.getId()
      )
  );

  private static void activateImpliedProfiles(
      final ConfigurableEnvironment environment,
      final Set<String> activeProfiles,
      final Set<String> baseProfiles,
      final Map<String, Set<String>> impliedProfilesMap,
      final String label,
      final Set<String> impliedProfiles
  ) {
    for (final String activeProfile : Sets.intersection(activeProfiles, baseProfiles)) {
      final Set<String> implied = impliedProfilesMap.getOrDefault(activeProfile, Set.of());
      implied.stream()
          .filter(p -> !activeProfiles.contains(p) && !impliedProfiles.contains(p))
          .forEach(p -> {
            LOGGER.warn(
                "Activating {} profile '{}' because profile '{}' is active",
                label, p, activeProfile
            );
            environment.addActiveProfile(p);
            impliedProfiles.add(p);
          });
    }
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment,
      final SpringApplication application) {
    String skipProcessor = System.getenv("SKIP_PROCESSOR");
    if (skipProcessor == null) {
      skipProcessor = "false";
    }
    if (skipProcessor.equalsIgnoreCase("true")) {
      LOGGER.info("SKIPPING PROCESSOR");
      return;
    }

    LOGGER.info("🚀 Streamlining Profiles...");

    final Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
    final Set<String> impliedProfiles = new HashSet<>();

    activateImpliedProfiles(
        environment,
        activeProfiles,
        LEGACY_PROFILES,
        IMPLIED_MODERN_PROFILES,
        "modern",
        impliedProfiles
    );

    activateImpliedProfiles(
        environment,
        activeProfiles,
        MODERN_PROFILES,
        IMPLIED_LEGACY_PROFILES,
        "legacy",
        impliedProfiles
    );
  }
}
