/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

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
  void shouldFormatOnlyMajorAndMinorAsAMinorVersionString() {
    assertThat(new SemanticVersion(8, 10, 3, null, null).toMinorVersionString()).isEqualTo("8.10");
    assertThat(new SemanticVersion(8, 10, 0, "SNAPSHOT", null).toMinorVersionString())
        .isEqualTo("8.10");
  }

  @Test
  void shouldStripThePreReleaseAndBuildMetadataSuffix() {
    assertThat(new SemanticVersion(8, 10, 0, "SNAPSHOT", "build.1").withoutPreRelease())
        .isEqualTo(new SemanticVersion(8, 10, 0, null, null));
    assertThat(new SemanticVersion(8, 10, 0, null, null).withoutPreRelease())
        .isEqualTo(new SemanticVersion(8, 10, 0, null, null));
  }

  @Test
  void shouldStripThePreReleaseSuffixFromAVersionString() {
    assertThat(SemanticVersion.withoutPreReleaseSuffix("8.10.0-SNAPSHOT")).isEqualTo("8.10.0");
    assertThat(SemanticVersion.withoutPreReleaseSuffix("8.10.0")).isEqualTo("8.10.0");
    assertThat(SemanticVersion.withoutPreReleaseSuffix("not-a-version"))
        .describedAs("falls back to the raw input when it can't be parsed")
        .isEqualTo("not-a-version");
  }
}
