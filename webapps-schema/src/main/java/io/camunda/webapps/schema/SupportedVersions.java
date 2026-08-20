/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupportedVersions {

  /**
   * Elasticsearch testcontainer image version. Sourced from {@code version.elasticsearch.container}
   * in {@code parent/pom.xml} via Maven resource filtering.
   */
  public static final String TEST_ELASTICSEARCH_VERSION;

  /**
   * OpenSearch testcontainer image version. Sourced from {@code version.opensearch.container} in
   * {@code parent/pom.xml} via Maven resource filtering.
   */
  public static final String TEST_OPENSEARCH_VERSION;

  private static final Logger LOG = LoggerFactory.getLogger(SupportedVersions.class);
  private static final String PROPERTIES_FILE = "testcontainer-versions.properties";

  static {
    final Properties props = new Properties();
    try (final InputStream in =
        SupportedVersions.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
      if (in != null) {
        props.load(in);
      } else {
        LOG.warn("Could not find {} on classpath", PROPERTIES_FILE);
      }
    } catch (final IOException e) {
      LOG.warn("Failed to load {}", PROPERTIES_FILE, e);
    }
    TEST_ELASTICSEARCH_VERSION = resolve(props, "elasticsearch.version", "8.19.16");
    TEST_OPENSEARCH_VERSION = resolve(props, "opensearch.version", "2.19.5");
  }

  private SupportedVersions() {}

  private static String resolve(final Properties props, final String key, final String fallback) {
    final String value = props.getProperty(key, fallback);
    if (value.startsWith("${")) {
      // Maven resource filtering has not run — properties file contains an unresolved placeholder.
      // This happens when tests are run from unfiltered sources (e.g. IDE without a prior build).
      LOG.warn(
          "Property {} in {} is unresolved (value: {}); falling back to {}",
          key,
          PROPERTIES_FILE,
          value,
          fallback);
      return fallback;
    }
    return value;
  }
}
