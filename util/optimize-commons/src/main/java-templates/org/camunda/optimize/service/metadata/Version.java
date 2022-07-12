/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import com.vdurmont.semver4j.Semver;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Version {
  public static final String RAW_VERSION = "${project.version}";
  public static final String VERSION = stripToPlainVersion(RAW_VERSION);
  public static final String VERSION_MAJOR = getMajorVersionFrom(VERSION);
  public static final String VERSION_MINOR = getMinorVersionFrom(VERSION);

  public static String stripToPlainVersion(final String rawVersion) {
    final String version = Arrays.stream(rawVersion.split("[^0-9]"))
      .limit(3)
      .filter(part -> part.chars().allMatch(Character::isDigit))
      .collect(Collectors.joining("."));
    return version + getPreviewNumberFrom(rawVersion).map(previewNumber -> "-preview-" + previewNumber).orElse("");
  }

  public static final String getMajorVersionFrom(final String plainVersion) {
    return String.valueOf(asSemver(plainVersion).getMajor());
  }

  public static final String getMinorVersionFrom(final String plainVersion) {
    return String.valueOf(asSemver(plainVersion).getMinor());
  }

  public static final String getPatchVersionFrom(final String plainVersion) {
    return String.valueOf(asSemver(plainVersion).getPatch());
  }

  public static Optional<String> getPreviewNumberFrom(final String version) {
    final Optional<String> previewSuffix = Arrays.stream(asSemver(version).getSuffixTokens())
      .filter(value -> value.contains("preview"))
      .findFirst();
    return previewSuffix.map(value -> value.split("-")[1]);
  }

  public static String getMajorAndMinor(final String currentVersion) {
    final Semver semver = asSemver(currentVersion);
    return semver.getMajor() + "." + semver.getMinor();
  }

  public static boolean isAlphaVersion(final String version) {
    return Arrays.stream(asSemver(version).getSuffixTokens())
      .anyMatch(value -> value.contains("alpha"));
  }

  private static Semver asSemver(final String plainVersion) {
    return new Semver(plainVersion, Semver.SemverType.LOOSE);
  }
}
