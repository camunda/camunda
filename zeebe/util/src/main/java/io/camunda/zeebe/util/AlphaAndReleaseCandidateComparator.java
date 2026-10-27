/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.apache.commons.lang3.StringUtils.isNumeric;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * This provides a slightly different implementation of the {@link SemanticVersion} comparator than
 * the default.
 *
 * <p>The main difference is how it handles pre-release comparison. The default will sort those by
 * splitting the pre-release string on "." and then comparing each part. This implementation splits
 * on numeric boundaries, so that "alpha1" will be considered less than "alpha10", and it treats
 * extra qualifiers like "-rc1" as lower precedence than their base pre-release (e.g.
 * "8.10.0-alpha4-rc1" < "8.10.0-alpha4" < "8.10.0-alpha11" < "8.10.0").
 *
 * <p>This should match how we actually sort our own versions in the wild.
 *
 * <p>This has been left optional so as not to affect any existing code that may be relying on the
 * default comparator behavior.
 */
class AlphaAndReleaseCandidateComparator implements Comparator<SemanticVersion> {

  @Override
  public int compare(final SemanticVersion v1, final SemanticVersion v2) {
    if (v1.major() != v2.major()) {
      return Integer.compare(v1.major(), v2.major());
    }
    if (v1.minor() != v2.minor()) {
      return Integer.compare(v1.minor(), v2.minor());
    }
    if (v1.patch() != v2.patch()) {
      return Integer.compare(v1.patch(), v2.patch());
    }
    return comparePreRelease(v1, v2);
  }

  private static int comparePreRelease(final SemanticVersion v1, final SemanticVersion v2) {
    if (v1.preRelease() == null && v2.preRelease() == null) {
      return 0;
    } else if (v1.preRelease() != null && v2.preRelease() == null) {
      return -1;
    } else if (v1.preRelease() == null && v2.preRelease() != null) {
      return 1;
    }

    final var preReleaseParts = splitPreRelease(Objects.requireNonNull(v1.preRelease()));
    final var otherPreReleaseParts = splitPreRelease(Objects.requireNonNull(v2.preRelease()));

    // sort by comparing the numeric parts as actual numbers (human readable) and the non-numeric
    // parts lexicographically (ASCII sort order)
    // this means that things like alpha2 will come before alpha10
    for (int i = 0; i < Math.min(preReleaseParts.size(), otherPreReleaseParts.size()); i++) {
      final var thisPart = preReleaseParts.get(i);
      final var otherPart = otherPreReleaseParts.get(i);

      if (isNumeric(thisPart) && isNumeric(otherPart)) {
        // Identifiers consisting of only digits are compared numerically.
        final var thisNumericPart = Integer.parseInt(thisPart);
        final var otherNumericPart = Integer.parseInt(otherPart);
        if (thisNumericPart != otherNumericPart) {
          return Integer.compare(thisNumericPart, otherNumericPart);
        }
      } else if (isNumeric(thisPart)) {
        // Numeric identifiers always have higher precedence than non-numeric identifiers.
        return 1;
      } else if (isNumeric(otherPart)) {
        return -1;
      } else {
        final var comparison = thisPart.compareTo(otherPart);
        if (comparison != 0) {
          return comparison;
        }
      }
    }

    // prefer the shorter version as that will indicate we have a non-RC version
    // e.g. 8.9.1-alpha1 is a later version than 8.9.1-alpha1-rc1
    return -Integer.compare(preReleaseParts.size(), otherPreReleaseParts.size());
  }

  private static List<String> splitPreRelease(final String preRelease) {
    return Arrays.stream(Objects.requireNonNull(preRelease).splitWithDelimiters("\\d+", 0))
        .filter(s -> !s.isEmpty())
        .toList();
  }
}
