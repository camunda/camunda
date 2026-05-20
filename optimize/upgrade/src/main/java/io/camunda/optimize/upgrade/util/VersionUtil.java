/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.util;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import java.util.Optional;
import org.slf4j.Logger;

public final class VersionUtil {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(VersionUtil.class);

  private VersionUtil() {}

  /**
   * Computes the previous minor version string (e.g. {@code "major.(minor-1)"}) from the given
   * version string.
   *
   * <p>Returns {@link Optional#empty()} in two cases:
   *
   * <ul>
   *   <li>The minor version is {@code 0} (major version boundary) — an explicit {@code
   *       UpgradePlanFactory} is required for that case. A warning is logged.
   *   <li>The version string cannot be parsed - an error is logged.
   * </ul>
   *
   * @param version a semver-compatible version string (e.g. {@code "8.10.0"})
   * @return an {@link Optional} containing the previous minor version as {@code "major.(minor-1)"}
   *     (e.g. {@code "8.9"}), or {@link Optional#empty()} if the minor version is {@code 0} or the
   *     version string is invalid
   */
  public static Optional<String> previousMinorVersion(final String version) {
    final Semver semver;
    final int minor;
    try {
      semver = new Semver(version, SemverType.LOOSE);
      minor = semver.getMinor();
    } catch (final Exception e) {
      LOG.error("Failed to parse version '{}': {}", version, e.getMessage(), e);
      return Optional.empty();
    }

    if (minor == 0) {
      LOG.warn(
          "Version '{}' is on a major version boundary (minor version is 0), so previous minor version cannot be computed",
          version);
      return Optional.empty();
    }

    return Optional.of(semver.getMajor() + "." + (minor - 1));
  }
}
