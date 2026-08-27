/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExporterMigrationTestHelperTest {

  @ParameterizedTest
  @MethodSource("providePreviousVersions")
  void shouldFindAllPatchVersionsOrLatestAlphaOrReleaseCandidate(
      final String previousMinorVersion,
      final List<String> allVersions,
      final List<String> expectedPreviousVersions) {
    assertThat(
            ExporterMigrationTestHelper.findAllPatchVersionsOrLatestAlphaOrReleaseCandidate(
                previousMinorVersion, allVersions))
        .isEqualTo(expectedPreviousVersions);
  }

  @Test
  void shouldFailIfNoMatchingVersionsFound() {
    final var allVersions = List.of("8.4.0", "8.4.1", "8.5.0");

    assertThatThrownBy(
            () ->
                ExporterMigrationTestHelper.findAllPatchVersionsOrLatestAlphaOrReleaseCandidate(
                    "8.3", allVersions))
        .hasMessage("No images found for 8.3")
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldFailIfNoMatchingPrereleaseVersionsFound() {
    final var allVersions = List.of("8.3.0-SNAPSHOT");

    assertThatThrownBy(
            () ->
                ExporterMigrationTestHelper.findAllPatchVersionsOrLatestAlphaOrReleaseCandidate(
                    "8.3", allVersions))
        .hasMessage("No release or pre-release images found for 8.3")
        .isInstanceOf(NoSuchElementException.class);
  }

  static Stream<Arguments> providePreviousVersions() {
    return Stream.of(
        Arguments.of(
            "8.3",
            List.of("8.3.0", "8.3.1", "8.3.2", "8.4.0", "8.4.1", "8.3.2-alpha1", "8.3.2-alpha2"),
            List.of("8.3.0", "8.3.1", "8.3.2")),
        Arguments.of(
            "8.4",
            List.of("8.3.0", "8.3.1", "8.3.2", "8.4.0", "8.4.1", "8.3.2-alpha1", "8.3.2-alpha2"),
            List.of("8.4.0", "8.4.1")),
        Arguments.of("8.3", List.of("8.3.2-alpha1", "8.3.2-alpha2"), List.of("8.3.2-alpha2")),
        Arguments.of(
            "8.3",
            List.of("8.3.2-alpha1", "8.3.2-alpha2", "8.3.2-alpha12"),
            List.of("8.3.2-alpha12")),
        Arguments.of(
            "8.3",
            List.of("8.3.2-alpha1", "8.3.2-alpha2", "8.3.2-alpha2-rc1"),
            List.of("8.3.2-alpha2")),
        Arguments.of("8.3", List.of("8.3.2-rc1"), List.of("8.3.2-rc1")),
        Arguments.of(
            "8.3", List.of("8.3.2-alpha1.1", "8.3.2-alpha1.1-rc1"), List.of("8.3.2-alpha1.1")));
  }
}
