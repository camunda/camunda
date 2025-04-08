/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.shared;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.shared.Profile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public record WorkingDirectoryConfiguration(Environment environment) {

  @Bean
  @ConditionalOnMissingBean
  public WorkingDirectory workingDirectory() {
    final Path workingDirectory;

    if (shouldUseTemporaryFolder()) {
      Loggers.SYSTEM_LOGGER.info(
          "Started with development/test mode; data will be deleted on shutdown");

      try {
        workingDirectory = Files.createTempDirectory("zeebe").toAbsolutePath().normalize();
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to start with temporary directory", e);
      }

      return new WorkingDirectory(workingDirectory, true);
    }

    workingDirectory =
        Path.of(environment.getProperty("basedir", ".")).toAbsolutePath().normalize();
    return new WorkingDirectory(workingDirectory, false);
  }

  private boolean shouldUseTemporaryFolder() {
    return environment.acceptsProfiles(
        Profiles.of(Profile.DEVELOPMENT.getId(), Profile.TEST.getId()));
  }

  public record WorkingDirectory(Path path, boolean isTemporary) {}
}
