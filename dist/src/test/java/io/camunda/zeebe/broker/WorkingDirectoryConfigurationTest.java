/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

final class WorkingDirectoryConfigurationTest {
  @ParameterizedTest
  @EnumSource(
      value = Profile.class,
      names = {"DEVELOPMENT", "TEST"},
      mode = Mode.INCLUDE)
  void shouldCreateTemporaryFolder(final Profile profile) {
    // given
    final var environment = new StandardEnvironment();
    environment.setActiveProfiles(profile.getId());

    // when
    final var config = new WorkingDirectoryConfiguration(environment);

    // then
    assertThat(config.workingDirectory().path()).isDirectory().exists();
  }

  @Test
  void shouldUseBasedirProperty(@TempDir final Path tmpDir) {
    // given
    final var profile = Profile.PRODUCTION;
    final var environment = new StandardEnvironment();
    final var properties = new Properties();
    environment.setActiveProfiles(profile.getId());
    environment.getPropertySources().addFirst(new PropertiesPropertySource("test", properties));
    properties.put("basedir", tmpDir.toString());

    // when
    final var config = new WorkingDirectoryConfiguration(environment);

    // then
    assertThat(config.workingDirectory().path()).isEqualTo(tmpDir);
  }
}
