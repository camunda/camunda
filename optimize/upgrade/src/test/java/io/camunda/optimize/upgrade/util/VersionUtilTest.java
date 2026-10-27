/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionUtilTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      textBlock =
          """
              1.1,    1.0
              8.10.0, 8.9
              8.10.5, 8.9
              9.99.9, 9.98
              """)
  void previousMinorVersionReturnsDecrementedMinor(final String version, final String expected) {
    assertThat(VersionUtil.previousMinorVersion(version)).contains(expected);
  }

  @ParameterizedTest(name = "{0} -> empty")
  @ValueSource(
      strings = {
        // minor version is 0, so previous minor version would be on major version boundary
        "9.0.0",
        // not a valid semver string
        "not-a-version",
        // missing minor segment
        "9"
      })
  void previousMinorVersionReturnsEmpty(final String version) {
    assertThat(VersionUtil.previousMinorVersion(version)).isEmpty();
  }
}
