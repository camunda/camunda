/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;

public final class VersionUtil {

  public static final Logger LOG = Loggers.FILE_LOGGER;

  public static final String VERSION_OVERRIDE_ENV_NAME = "ZEEBE_VERSION_OVERRIDE";
  private static final String VERSION_PROPERTIES_PATH = "/zeebe-util.properties";
  private static final String VERSION_PROPERTY_NAME = "zeebe.version";
  private static final String LAST_VERSION_PROPERTY_NAME = "zeebe.last.version";
  private static final String VERSION_DEV = "development";

  private static String version;
  private static String versionLowerCase;
  private static String lastVersion;

  private VersionUtil() {}

  /**
   * @return the current version or 'development' if none can be determined.
   */
  public static String getVersion() {
    if (version != null) {
      return version;
    }
    final var foundVersion =
        Optional.ofNullable(System.getenv(VERSION_OVERRIDE_ENV_NAME))
            .or(() -> Optional.ofNullable(readProperty(VERSION_PROPERTY_NAME)))
            .or(
                () ->
                    Optional.ofNullable(VersionUtil.class.getPackage().getImplementationVersion()));
    if (foundVersion.isPresent()) {
      version = foundVersion.get();
    } else {
      LOG.warn(
          "Version is not found in env, version file or package, using '%s' instead"
              .formatted(VERSION_DEV));
      version = VERSION_DEV;
    }
    return version;
  }

  /**
   * @return the current version if it can be parsed as a semantic version, otherwise empty.
   */
  public static Optional<SemanticVersion> getSemanticVersion() {
    return SemanticVersion.parse(getVersion());
  }

  public static String getVersionLowerCase() {
    if (versionLowerCase == null) {
      versionLowerCase = getVersion().toLowerCase();
    }
    return versionLowerCase;
  }

  /**
   * Returns the configured backwards-compatibility baseline version used by Zeebe tests and RevAPI
   * checks.
   *
   * <p>The value is read from the {@code zeebe.last.version} property of {@code
   * /zeebe-util.properties}, which is populated at build time from the Maven property {@code
   * backwards.compat.version} declared in {@code parent/pom.xml} (commented there as "version
   * against which backwards compatibility is checked").
   *
   * <p>By convention:
   *
   * <ul>
   *   <li>On {@code main}, this baseline points to a release of the previous minor version (e.g.
   *       while {@code main} targets 8.8.x, the baseline is an 8.7.x release).
   *   <li>On a stable branch (e.g. {@code stable/8.7}), it points to a release of the previous
   *       stable minor (e.g. an 8.6.x release).
   * </ul>
   *
   * <p>The property is a <em>manually maintained</em> baseline, not "the latest previous patch at
   * the moment of invocation" -- its value is only updated when a new baseline is intentionally
   * chosen, and it can legitimately differ between branches. This method is currently referenced
   * from tests only.
   *
   * @return the configured backwards-compatibility baseline, or {@code null} if the property is not
   *     set or the properties file cannot be read.
   */
  public static String getPreviousVersion() {
    if (lastVersion == null) {
      lastVersion = readProperty(LAST_VERSION_PROPERTY_NAME);
    }

    return lastVersion;
  }

  public static Optional<SemanticVersion> getPreviousSemanticVersion() {
    return SemanticVersion.parse(getPreviousVersion());
  }

  /**
   * Overrides the cached version with the given value. This is intended for testing only, to allow
   * setting a specific version without relying on environment variables or properties files.
   *
   * <p>Call {@link #resetVersionForTesting()} to clear the override and allow the version to be
   * re-read from the normal sources.
   */
  @VisibleForTesting("Allow tests to override the version without setting env vars")
  public static void overrideVersionForTesting(final String versionOverride) {
    version = versionOverride;
    versionLowerCase = null;
  }

  /**
   * Resets the cached version so that the next call to {@link #getVersion()} will re-read it from
   * the normal sources (env var, properties file, or package). This is intended for testing only.
   */
  @VisibleForTesting("Allow tests to reset the version after overriding it")
  public static void resetVersionForTesting() {
    version = null;
    versionLowerCase = null;
  }

  private static String readProperty(final String property) {
    try (final InputStream lastVersionFileStream =
        VersionUtil.class.getResourceAsStream(VERSION_PROPERTIES_PATH)) {
      final Properties props = new Properties();
      props.load(lastVersionFileStream);

      return props.getProperty(property);
    } catch (final IOException e) {
      LOG.error(String.format("Can't read version file: %s", VERSION_PROPERTIES_PATH), e);
    }

    return null;
  }
}
