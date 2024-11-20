/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.MigrationsModuleConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneMigration {

  private static final Pattern MIGRATION_PROFILE_REGEX = Pattern.compile("^(.+-)?migration$");

  public static void main(final String[] args) {
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    final String[] profiles = enrichProfiles();

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(MigrationsModuleConfiguration.class)
            .profiles(profiles)
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener(), new ApplicationTerminateListener(profiles))
            .build(args);

    application.run(args);
  }

  private static String[] enrichProfiles() {
    String[] profiles = System.getProperty("spring.profiles.active").split(",");
    if (Arrays.stream(profiles).noneMatch(p -> MIGRATION_PROFILE_REGEX.matcher(p).matches())) {
      profiles = appendProfile(profiles, Profile.MIGRATION.getId());
    }

    if (Arrays.asList(profiles).contains(Profile.MIGRATION.getId())) {
      profiles = appendProfile(profiles, Profile.IDENTITY_MIGRATION.getId());
      profiles = appendProfile(profiles, Profile.PROCESS_MIGRATION.getId());
    }

    return profiles;
  }

  private static String[] appendProfile(final String[] profiles, final String profile) {
    final String[] modifiedProfiles = Arrays.copyOf(profiles, profiles.length + 1);
    modifiedProfiles[modifiedProfiles.length - 1] = profile;
    return modifiedProfiles;
  }

  public static class ApplicationTerminateListener
      implements ApplicationListener<MigrationFinishedEvent> {

    private int exitCode = 0;
    private int arrivedEvents = 0;
    private final int awaitEvents;

    public ApplicationTerminateListener(final String[] profiles) {
      if (Arrays.asList(profiles).contains(Profile.MIGRATION.getId())) {
        awaitEvents = 2;
      } else {
        awaitEvents =
            (int)
                Arrays.stream(profiles)
                    .filter(p -> MIGRATION_PROFILE_REGEX.matcher(p).matches())
                    .count();
      }
    }

    @Override
    public void onApplicationEvent(final MigrationFinishedEvent event) {
      final int errorCode = (int) event.getSource();
      if (errorCode < exitCode) {
        exitCode = errorCode;
      }
      if (++arrivedEvents == awaitEvents) {
        System.exit(exitCode);
      }
    }
  }

  public static class MigrationFinishedEvent extends ApplicationEvent {

    public MigrationFinishedEvent(final int errorCode) {
      super(errorCode);
    }
  }
}
