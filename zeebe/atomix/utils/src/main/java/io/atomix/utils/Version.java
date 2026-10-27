/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils;

import io.camunda.zeebe.util.SemanticVersion;
import java.util.Objects;

/**
 * Atomix software version.
 *
 * <p>NOTE: eventually we should stop using this and use {@link SemanticVersion} directly instead.
 */
public final class Version implements Comparable<Version> {

  private final int major;
  private final int minor;
  private final int patch;
  private final String build;
  private final String metadata;

  private Version(
      final int major,
      final int minor,
      final int patch,
      final String build,
      final String metadata) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.build = build;
    this.metadata = metadata;
  }

  public int major() {
    return major;
  }

  public int minor() {
    return minor;
  }

  public int patch() {
    return patch;
  }

  public String preRelease() {
    return build;
  }

  public String buildMetadata() {
    return metadata;
  }

  /**
   * Returns a new version from the given version string.
   *
   * @param version the version string
   * @return the version object
   * @throws IllegalArgumentException if the version string is invalid
   */
  public static Version from(final String version) {
    final SemanticVersion semanticVersion =
        SemanticVersion.parse(version)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected to parse valid semantic version, but got [%s]"
                            .formatted(version)));

    return new Version(
        semanticVersion.major(),
        semanticVersion.minor(),
        semanticVersion.patch(),
        semanticVersion.preRelease(),
        semanticVersion.buildMetadata());
  }

  @Override
  public int compareTo(final Version that) {
    return toSemanticVersion().compareTo(that.toSemanticVersion());
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, build);
  }

  @Override
  public boolean equals(final Object object) {
    if (!(object instanceof Version)) {
      return false;
    }

    final Version that = (Version) object;
    return major == that.major
        && minor == that.minor
        && patch == that.patch
        && Objects.equals(build, that.build);
  }

  @Override
  public String toString() {
    return toSemanticVersion().toString();
  }

  private SemanticVersion toSemanticVersion() {
    return new SemanticVersion(major, minor, patch, build, null);
  }
}
