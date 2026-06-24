/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Resolves the analytics exporter's own version from {@code /analytics-exporter.properties}, which
 * is filtered at build time to substitute the Maven {@code project.version}.
 *
 * <p>Kept distinct from {@link io.camunda.zeebe.util.VersionUtil} so the exporter can be versioned
 * independently of the broker when it is published as a standalone artifact.
 */
final class AnalyticsExporterVersion {

  private static final String PROPERTIES_PATH = "/analytics-exporter.properties";
  private static final String VERSION_PROPERTY = "analytics-exporter.version";
  private static final String VERSION = readVersion();

  static String get() {
    return VERSION;
  }

  private static String readVersion() {
    try (final InputStream in =
        AnalyticsExporterVersion.class.getResourceAsStream(PROPERTIES_PATH)) {
      if (in == null) {
        throw new IllegalStateException(
            PROPERTIES_PATH + " missing from classpath — build artifact is incomplete");
      }
      final var props = new Properties();
      props.load(in);
      final var version = props.getProperty(VERSION_PROPERTY);
      if (version == null || version.isBlank()) {
        throw new IllegalStateException(VERSION_PROPERTY + " not set in " + PROPERTIES_PATH);
      }
      return version;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read " + PROPERTIES_PATH, e);
    }
  }
}
