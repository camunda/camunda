/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metadata;

import com.vdurmont.semver4j.Semver;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class Version {
  public static final String RAW_VERSION = "${project.version}";
  public static final String VERSION = stripToPlainVersion(RAW_VERSION);
  public static final String VERSION_MAJOR = getMajorVersionFrom(VERSION);
  public static final String VERSION_MINOR = getMinorVersionFrom(VERSION);

  public static String stripToPlainVersion(final String rawVersion) {
    return Arrays.stream(rawVersion.split("[^0-9]"))
        .limit(3)
        .filter(part -> part.chars().allMatch(Character::isDigit))
        .collect(Collectors.joining("."));
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
