/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import io.camunda.zeebe.util.SemanticVersion;

/** Checks the compatibility of the current version with the version that ran state migrations. */
final class VersionCompatibilityCheck {
  private VersionCompatibilityCheck() {}

  /**
   * Checks the compatibility of the current version with the previous Version
   *
   * @param previousVersion a string representation of the previous version or null if unknown
   * @param currentVersion a string representation of the current version or null if unknown
   * @return a tri-state object indicating the compatibility of the versions:
   *     <ul>
   *       <li>{@link Indeterminate} if either of the versions are not available or version is not a
   *           valid semantic version.
   *       <li>{@link Incompatible} if the current version is not compatible with the previous
   *           version, e.g. a downgrade or a skipped minor version.
   *       <li>{@link Compatible} if the current version is compatible with the previous version,
   *           e.g. upgrade to the next minor version.
   */
  public static CheckResult check(final String previousVersion, final String currentVersion) {
    if (previousVersion == null) {
      return new Indeterminate.PreviousVersionUnknown(currentVersion);
    }
    if (currentVersion == null) {
      return new Indeterminate.CurrentVersionUnknown(previousVersion);
    }

    final var parsedPreviousVersion = SemanticVersion.parse(previousVersion);
    final var parsedCurrentVersion = SemanticVersion.parse(currentVersion);

    if (parsedPreviousVersion.isEmpty()) {
      return new Indeterminate.PreviousVersionInvalid(previousVersion, currentVersion);
    }

    if (parsedCurrentVersion.isEmpty()) {
      return new Indeterminate.CurrentVersionInvalid(previousVersion, currentVersion);
    }

    final var previous = parsedPreviousVersion.get();
    final var current = parsedCurrentVersion.get();

    if (previous.compareTo(current) == 0) {
      return new Compatible.SameVersion(current);
    } else if (previous.preRelease() != null || current.preRelease() != null) {
      return new Incompatible.UseOfPreReleaseVersion(previous, current);
    } else if (previous.compareTo(current) > 0) {
      if (previous.major() > current.major()) {
        return new Incompatible.MajorDowngrade(previous, current);
      } else if (previous.minor() > current.minor()) {
        return new Incompatible.MinorDowngrade(previous, current);
      } else {
        return new Incompatible.PatchDowngrade(previous, current);
      }
    } else {
      if (previous.major() < current.major()) {
        return new Incompatible.MajorUpgrade(previous, current);
      } else if (previous.minor() - current.minor() < -1) {
        return new Incompatible.SkippedMinorVersion(previous, current);
      } else if (previous.minor() < current.minor()) {
        return new Compatible.MinorUpgrade(previous, current);
      } else {
        return new Compatible.PatchUpgrade(previous, current);
      }
    }
  }

  sealed interface CheckResult {
    sealed interface Indeterminate extends CheckResult {
      record PreviousVersionUnknown(String currentVersion) implements Indeterminate {}

      record CurrentVersionUnknown(String previousVersion) implements Indeterminate {}

      record PreviousVersionInvalid(String previousVersion, String currentVersion)
          implements Indeterminate {}

      record CurrentVersionInvalid(String previousVersion, String currentVersion)
          implements Indeterminate {}
    }

    sealed interface Incompatible extends CheckResult {
      record MajorUpgrade(SemanticVersion from, SemanticVersion to) implements Incompatible {}

      record SkippedMinorVersion(SemanticVersion from, SemanticVersion to)
          implements Incompatible {}

      record UseOfPreReleaseVersion(SemanticVersion from, SemanticVersion to)
          implements Incompatible {}

      record PatchDowngrade(SemanticVersion from, SemanticVersion to) implements Incompatible {}

      record MinorDowngrade(SemanticVersion from, SemanticVersion to) implements Incompatible {}

      record MajorDowngrade(SemanticVersion from, SemanticVersion to) implements Incompatible {}
    }

    sealed interface Compatible extends CheckResult {
      record SameVersion(SemanticVersion version) implements Compatible {}

      record PatchUpgrade(SemanticVersion from, SemanticVersion to) implements Compatible {}

      record MinorUpgrade(SemanticVersion from, SemanticVersion to) implements Compatible {}
    }
  }
}
