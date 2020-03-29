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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Integer.parseInt;

import com.google.common.collect.ComparisonChain;
import java.util.Objects;

/** Atomix software version. */
public final class Version implements Comparable<Version> {

  private final int major;
  private final int minor;
  private final int patch;
  private final String build;

  private Version(final int major, final int minor, final int patch, final String build) {
    checkArgument(major >= 0, "major version must be >= 0");
    checkArgument(minor >= 0, "minor version must be >= 0");
    checkArgument(patch >= 0, "patch version must be >= 0");
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.build = Build.from(build).toString();
  }

  /**
   * Returns a new version from the given version string.
   *
   * @param version the version string
   * @return the version object
   * @throws IllegalArgumentException if the version string is invalid
   */
  public static Version from(final String version) {
    final String[] fields = version.split("[.-]", 4);
    checkArgument(fields.length >= 3, "version number is invalid");
    return new Version(
        parseInt(fields[0]),
        parseInt(fields[1]),
        parseInt(fields[2]),
        fields.length == 4 ? fields[3] : null);
  }

  /**
   * Returns a new version from the given parts.
   *
   * @param major the major version number
   * @param minor the minor version number
   * @param patch the patch version number
   * @param build the build version number
   * @return the version object
   */
  public static Version from(
      final int major, final int minor, final int patch, final String build) {
    return new Version(major, minor, patch, build);
  }

  /**
   * Returns the major version number.
   *
   * @return the major version number
   */
  public int major() {
    return major;
  }

  /**
   * Returns the minor version number.
   *
   * @return the minor version number
   */
  public int minor() {
    return minor;
  }

  /**
   * Returns the patch version number.
   *
   * @return the patch version number
   */
  public int patch() {
    return patch;
  }

  /**
   * Returns the build version number.
   *
   * @return the build version number
   */
  public String build() {
    return build;
  }

  @Override
  public int compareTo(final Version that) {
    return ComparisonChain.start()
        .compare(this.major, that.major)
        .compare(this.minor, that.minor)
        .compare(this.patch, that.patch)
        .compare(Build.from(this.build), Build.from(that.build))
        .result();
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
    return this.major == that.major
        && this.minor == that.minor
        && this.patch == that.patch
        && Objects.equals(this.build, that.build);
  }

  @Override
  public String toString() {
    final StringBuilder builder =
        new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
    final String build = Build.from(this.build).toString();
    if (build != null) {
      builder.append('-').append(build);
    }
    return builder.toString();
  }

  /** Build version. */
  private static final class Build implements Comparable<Build> {

    private final Type type;
    private final int version;

    private Build(final Type type, final int version) {
      this.type = type;
      this.version = version;
    }

    /**
     * Creates a new build version from the given string.
     *
     * @param build the build version string
     * @return the build version
     * @throws IllegalArgumentException if the build version string is invalid
     */
    public static Build from(final String build) {
      if (build == null) {
        return new Build(Type.FINAL, 0);
      } else if (build.equalsIgnoreCase(Type.SNAPSHOT.name())) {
        return new Build(Type.SNAPSHOT, 0);
      }

      for (final Type type : Type.values()) {
        if (type.name != null
            && build.length() >= type.name.length()
            && build.substring(0, type.name.length()).equalsIgnoreCase(type.name)) {
          try {
            final int version = parseInt(build.substring(type.name.length()));
            return new Build(type, version);
          } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(build + " is not a valid build version string");
          }
        }
      }
      throw new IllegalArgumentException(build + " is not a valid build version string");
    }

    @Override
    public int compareTo(final Build that) {
      return ComparisonChain.start()
          .compare(this.type.ordinal(), that.type.ordinal())
          .compare(this.version, that.version)
          .result();
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, version);
    }

    @Override
    public boolean equals(final Object object) {
      if (!(object instanceof Build)) {
        return false;
      }
      final Build that = (Build) object;
      return Objects.equals(this.type, that.type) && this.version == that.version;
    }

    @Override
    public String toString() {
      return type.format(version);
    }

    /** Build type. */
    private enum Type {
      SNAPSHOT("snapshot"),
      ALPHA("alpha"),
      BETA("beta"),
      RC("rc"),
      FINAL(null);

      private final String name;

      Type(final String name) {
        this.name = name;
      }

      String format(final int version) {
        if (name == null) {
          return null;
        } else if ("snapshot".equals(name)) {
          return "SNAPSHOT";
        } else {
          return String.format("%s%d", name, version);
        }
      }

      @Override
      public String toString() {
        return name;
      }
    }
  }
}
