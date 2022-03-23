/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Version {
  public static final String RAW_VERSION = "${project.version}";
  public static final String VERSION = stripToPlainVersion(RAW_VERSION);
  public static final String VERSION_MAJOR = getMajorVersionFrom(VERSION);
  public static final String VERSION_MINOR = getMinorVersionFrom(VERSION);
  public static final String VERSION_PATCH = getPatchVersionFrom(VERSION);
  public static final String INVALID_VERSION_MSG = "Provided version does not satisfy the x.x.x pattern: ";

  public static final String stripToPlainVersion(final String rawVersion) {
    // extract plain <major>.<minor>.<patch> version, strip everything else
    return Arrays.stream(rawVersion.split("[^0-9]"))
      .limit(3)
      .filter(part -> part.chars().allMatch(Character::isDigit))
      .collect(Collectors.joining("."));
  }

  public static final String getMajorVersionFrom(String plainVersion) {
    return Arrays.stream(plainVersion.split("\\.")).findFirst()
      .orElseThrow(() -> new IllegalArgumentException(INVALID_VERSION_MSG + plainVersion));
  }

  public static final String getMinorVersionFrom(String plainVersion) {
    return Arrays.stream(plainVersion.split("\\.")).skip(1).findFirst()
      .orElseThrow(() -> new IllegalArgumentException(INVALID_VERSION_MSG + plainVersion));
  }

  public static final String getPatchVersionFrom(String plainVersion) {
    return Arrays.stream(plainVersion.split("\\.")).skip(2).findFirst()
      .orElseThrow(() -> new IllegalArgumentException(INVALID_VERSION_MSG + plainVersion));
  }

  public static String getMajorAndMinor(String currentVersion) {
    return getMajorVersionFrom(currentVersion) + "." + getMinorVersionFrom(currentVersion);
  }
}
