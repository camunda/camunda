/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class SemanticVersionTest {
  @Test
  void shouldParseCurrentVersion() {
    assertThat(SemanticVersion.parse(VersionUtil.getVersion())).isPresent();
  }

  @Test
  void shouldParseBasicSemanticVersions() {
    assertThat(SemanticVersion.parse("1.2.3")).contains(new SemanticVersion(1, 2, 3, null, null));
    assertThat(SemanticVersion.parse("0.0.0")).contains(new SemanticVersion(0, 0, 0, null, null));
    assertThat(SemanticVersion.parse("10.20.30"))
        .contains(new SemanticVersion(10, 20, 30, null, null));
  }

  @Test
  void shouldParseVersionsWithPreRelease() {
    assertThat(SemanticVersion.parse("1.2.3-alpha"))
        .contains(new SemanticVersion(1, 2, 3, "alpha", null));
    assertThat(SemanticVersion.parse("1.2.3-beta"))
        .contains(new SemanticVersion(1, 2, 3, "beta", null));
    assertThat(SemanticVersion.parse("1.2.3-rc"))
        .contains(new SemanticVersion(1, 2, 3, "rc", null));
    assertThat(SemanticVersion.parse("1.2.3-SNAPSHOT"))
        .contains(new SemanticVersion(1, 2, 3, "SNAPSHOT", null));
  }

  @Test
  void shouldParseVersionsWithBuildMetadata() {
    assertThat(SemanticVersion.parse("1.2.3+build"))
        .contains(new SemanticVersion(1, 2, 3, null, "build"));
    assertThat(SemanticVersion.parse("1.2.3+build.1"))
        .contains(new SemanticVersion(1, 2, 3, null, "build.1"));
    assertThat(SemanticVersion.parse("1.2.3+build.1.2"))
        .contains(new SemanticVersion(1, 2, 3, null, "build.1.2"));
  }

  @Test
  void shouldParseVersionsWithPreReleaseAndBuildMetadata() {
    assertThat(SemanticVersion.parse("1.2.3-alpha+build"))
        .contains(new SemanticVersion(1, 2, 3, "alpha", "build"));
    assertThat(SemanticVersion.parse("1.2.3-alpha+build.1"))
        .contains(new SemanticVersion(1, 2, 3, "alpha", "build.1"));
    assertThat(SemanticVersion.parse("1.2.3-alpha+build.1.2"))
        .contains(new SemanticVersion(1, 2, 3, "alpha", "build.1.2"));
  }

  @Test
  void shouldCompareBasicVersions() {
    assertThat(new SemanticVersion(1, 2, 3, null, null))
        .isLessThan(new SemanticVersion(1, 2, 4, null, null))
        .isLessThan(new SemanticVersion(1, 3, 7, null, null))
        .isLessThan(new SemanticVersion(2, 4, 0, null, null));
  }

  @Test
  void shouldCompareVersionsWithPreRelease() {
    assertThat(new SemanticVersion(1, 2, 3, "alpha", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "alpha.1", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "alpha.beta", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "beta", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "beta.2", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "beta.11", null))
        .isLessThan(new SemanticVersion(1, 2, 3, "rc.1", null))
        .isLessThan(new SemanticVersion(1, 2, 3, null, null));
  }

  @Test
  void shouldCompareVersionsWithBuildMetadata() {
    assertThat(new SemanticVersion(1, 0, 0, "alpha", "build.1"))
        .isEqualByComparingTo(new SemanticVersion(1, 0, 0, "alpha", "build.2"));
  }

  @Test
  void shouldSortAlphaAndReleaseCandidateVersions() {
    final var versions =
        Stream.of(
                "1.0.0",
                "2.0.0",
                "2.0.0-rc3",
                "2.0.0-SNAPSHOT",
                "2.1.0-alpha3",
                "2.1.0",
                "1.0.0-alpha1",
                "1.0.0-alpha1-rc1",
                "1.0.0-alpha1-rc10",
                "1.0.0-alpha1-rc2",
                "1.0.0-alpha2-rc1",
                "1.0.1-alpha2-rc1")
            .map(SemanticVersion::parse)
            .map(Optional::orElseThrow)
            .toList();

    final var sorted =
        versions.stream()
            .sorted(SemanticVersion.ALPHA_AND_RELEASE_CANDIDATE_COMPARATOR)
            .map(SemanticVersion::toString)
            .toList();

    assertThat(sorted)
        .isEqualTo(
            List.of(
                "1.0.0-alpha1-rc1",
                "1.0.0-alpha1-rc2",
                "1.0.0-alpha1-rc10",
                "1.0.0-alpha1",
                "1.0.0-alpha2-rc1",
                "1.0.0",
                "1.0.1-alpha2-rc1",
                "2.0.0-SNAPSHOT",
                "2.0.0-rc3",
                "2.0.0",
                "2.1.0-alpha3",
                "2.1.0"));
  }
}
