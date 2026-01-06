/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.apache.commons.lang3.StringUtils.isNumeric;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A semantic version as specified by <a href="https://semver.org/">Semantic Versioning 2.0.0</a>.
 *
 * <p>Note that the implementation of {@link Comparable} is not consistent with {@link
 * Object#equals(Object)} because SemVer does not consider build metadata when comparing versions.
 */
public record SemanticVersion(
    int major, int minor, int patch, String preRelease, String buildMetadata)
    implements Comparable<SemanticVersion> {

  private static final ConcurrentHashMap<String, SemanticVersion> CACHE =
      new ConcurrentHashMap<>(16);

  /**
   * @see <a
   *     href="https://semver.org/spec/v2.0.0.html#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string">
   *     Suggested Regex to parse SemVer </a>
   */
  private static final Pattern PATTERN =
      Pattern.compile(
          """
              ^(?<major>0|[1-9]\\d*)\
              \\.(?<minor>0|[1-9]\\d*)\
              \\.(?<patch>0|[1-9]\\d*)\
              (?:-(?<preRelease>(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?\
              (?:\\+(?<buildMetadata>[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$""");

  public SemanticVersion {
    if (major < 0) {
      throw new IllegalArgumentException("Major version must be non-negative");
    }
    if (minor < 0) {
      throw new IllegalArgumentException("Minor version must be non-negative");
    }
    if (patch < 0) {
      throw new IllegalArgumentException("Patch version must be non-negative");
    }
  }

  public boolean isPreRelease() {
    return preRelease != null;
  }

  public static Optional<SemanticVersion> parse(final String version) {
    if (version == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(CACHE.computeIfAbsent(version, SemanticVersion::doParse));
  }

  private static SemanticVersion doParse(final String version) {
    final var matcher = PATTERN.matcher(version);
    if (matcher.matches()) {
      final var major = Integer.parseInt(matcher.group("major"));
      final var minor = Integer.parseInt(matcher.group("minor"));
      final var patch = Integer.parseInt(matcher.group("patch"));
      final var preRelease = matcher.group("preRelease");
      final var buildMetadata = matcher.group("buildMetadata");
      return new SemanticVersion(major, minor, patch, preRelease, buildMetadata);
    } else {
      return null;
    }
  }

  @Override
  public int compareTo(final SemanticVersion other) {
    // Precedence is determined by the first difference when comparing each of these identifiers
    // from left to right as follows: Major, minor, and patch versions are always compared
    // numerically.
    if (major != other.major) {
      return Integer.compare(major, other.major);
    }
    if (minor != other.minor) {
      return Integer.compare(minor, other.minor);
    }
    if (patch != other.patch) {
      return Integer.compare(patch, other.patch);
    }
    return comparePreRelease(other);
  }

  private int comparePreRelease(final SemanticVersion other) {
    if (preRelease == null && other.preRelease == null) {
      return 0;
    } else if (preRelease != null && other.preRelease == null) {
      // A pre-release version has lower precedence than a normal version
      return -1;
    } else //noinspection ConstantValue -- makes this more readable
    if (preRelease == null && other.preRelease != null) {
      return 1;
    }

    final var preReleaseParts = preRelease.split("\\.");
    final var otherPreReleaseParts = other.preRelease.split("\\.");

    // Precedence for two pre-release versions with the same major, minor, and patch version MUST be
    // determined by comparing each dot separated identifier from left to right until a difference
    // is found.

    for (int i = 0; i < Math.min(preReleaseParts.length, otherPreReleaseParts.length); i++) {
      final var thisPart = preReleaseParts[i];
      final var otherPart = otherPreReleaseParts[i];

      if (isNumeric(thisPart) && isNumeric(otherPart)) {
        // Identifiers consisting of only digits are compared numerically.
        final var thisNumericPart = Integer.parseInt(thisPart);
        final var otherNumericPart = Integer.parseInt(otherPart);
        if (thisNumericPart != otherNumericPart) {
          return Integer.compare(thisNumericPart, otherNumericPart);
        }
      } else if (isNumeric(thisPart)) {
        // Numeric identifiers always have lower precedence than non-numeric identifiers.
        return -1;
      } else if (isNumeric(otherPart)) {
        return 1;
      } else {
        // Identifiers with letters or hyphens are compared lexically in ASCII sort order.
        final var comparison = thisPart.compareTo(otherPart);
        if (comparison != 0) {
          return comparison;
        }
      }
    }

    return Integer.compare(preReleaseParts.length, otherPreReleaseParts.length);
  }

  @Override
  public String toString() {
    final var version = major + "." + minor + "." + patch;
    if (preRelease != null) {
      return version + "-" + preRelease;
    }
    return version;
  }
}
