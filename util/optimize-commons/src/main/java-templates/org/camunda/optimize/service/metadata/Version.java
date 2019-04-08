/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Version {
  public static final String RAW_VERSION = "${project.version}";
  public static final String VERSION = stripToPlainVersion(RAW_VERSION);
  public static final String VERSION_MAJOR = getMajorVersionFrom(VERSION);
  public static final String VERSION_MINOR = getMinorVersionFrom(VERSION);
  public static final String VERSION_PATCH = getPatchVersionFrom(VERSION);

  public static final String stripToPlainVersion(final String rawVersion) {
    // extract plain <major>.<minor>.<patch> version, strip everything else
    return Arrays.stream(rawVersion.split("[^0-9]"))
      .limit(3)
      .filter(part -> part.chars().allMatch(Character::isDigit))
      .collect(Collectors.joining("."));
  }

  public static final String getMajorVersionFrom(String plainVersion) {
    return plainVersion.split("\\.")[0];
  }

  public static final String getMinorVersionFrom(String plainVersion) {
    return plainVersion.split("\\.")[1];
  }

  public static final String getPatchVersionFrom(String plainVersion) {
    return plainVersion.split("\\.")[2];
  }

  public static String getMajorAndMinor(String currentVersion) {
    return getMajorVersionFrom(currentVersion) + "." + getMinorVersionFrom(currentVersion);
  }
}
