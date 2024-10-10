/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.service;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag(OPENSEARCH_PASSING)
public class UpgradeValidationServiceIT {

  private final UpgradeValidationService underTest = new UpgradeValidationService();

  @ParameterizedTest
  @MethodSource("invalidSchemaVersionScenarios")
  public void versionValidationInvalid(
      final String schemaVersion, final String fromVersion, final String toVersion) {
    // when
    assertThatThrownBy(
            () -> underTest.validateSchemaVersions(schemaVersion, fromVersion, toVersion))
        // then
        .isInstanceOf(UpgradeRuntimeException.class);
  }

  @ParameterizedTest
  @MethodSource("validSchemaVersionScenarios")
  public void versionValidationValid(
      final String schemaVersion, final String fromVersion, final String toVersion) {
    // when
    assertThatNoException()
        .isThrownBy(() -> underTest.validateSchemaVersions(schemaVersion, fromVersion, toVersion));
  }

  @Test
  public void validateEnvironmentFailsWithoutEnvironmentConfig() throws Exception {
    // given
    deleteEnvConfig();

    // when
    assertThatThrownBy(underTest::validateEnvironmentConfigInClasspath)
        // then
        .isInstanceOf(UpgradeRuntimeException.class)
        .hasMessage("Couldn't read environment-config.yaml from config folder in Optimize root!");
  }

  @Test
  public void validateEnvironmentSucceeds() throws Exception {
    // given
    createEmptyEnvConfig();

    // when & then
    assertThatNoException().isThrownBy(underTest::validateEnvironmentConfigInClasspath);
  }

  private static Stream<Arguments> invalidSchemaVersionScenarios() {
    return Stream.of(
        Arguments.of("Test", "2.0", "2.1"),
        Arguments.of("", "2.0", "2.1"),
        Arguments.of("1.9", "2.0", "2.1"),
        Arguments.of("2.0", "", "2.1"),
        Arguments.of("2.0", "2.1", ""));
  }

  private static Stream<Arguments> validSchemaVersionScenarios() {
    return Stream.of(Arguments.of("2.0", "2.0", "2.1"), Arguments.of("2.1", "2.0", "2.1"));
  }
}
