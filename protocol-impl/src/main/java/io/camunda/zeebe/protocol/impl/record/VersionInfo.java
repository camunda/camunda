/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record;

import java.util.Objects;
import java.util.regex.Pattern;

public final class VersionInfo {

  public static final VersionInfo UNKNOWN = new VersionInfo(0, 0, 0);

  private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+).*");

  private final int majorVersion;
  private final int minorVersion;
  private final int patchVersion;

  public VersionInfo(final int majorVersion, final int minorVersion, final int patchVersion) {
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
    this.patchVersion = patchVersion;
  }

  public static VersionInfo parse(final String version) {
    final var matcher = VERSION_PATTERN.matcher(version);
    if (matcher.matches()) {
      final var major = Integer.parseInt(matcher.group(1));
      final var minor = Integer.parseInt(matcher.group(2));
      final var patch = Integer.parseInt(matcher.group(3));
      return new VersionInfo(major, minor, patch);

    } else {
      return UNKNOWN;
    }
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public int getPatchVersion() {
    return patchVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hash(majorVersion, minorVersion, patchVersion);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VersionInfo that = (VersionInfo) o;
    return majorVersion == that.majorVersion
        && minorVersion == that.minorVersion
        && patchVersion == that.patchVersion;
  }

  @Override
  public String toString() {
    return majorVersion + "." + minorVersion + "." + patchVersion;
  }
}
