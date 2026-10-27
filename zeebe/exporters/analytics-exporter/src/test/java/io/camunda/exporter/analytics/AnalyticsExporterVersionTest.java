/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AnalyticsExporterVersionTest {

  @Test
  void shouldResolveVersionFromFilteredProperties() throws IOException {
    // given — read the same properties file the production code reads
    final var props = new Properties();
    try (final var in = getClass().getResourceAsStream("/analytics-exporter.properties")) {
      assertThat(in).as("analytics-exporter.properties must be on the classpath").isNotNull();
      props.load(in);
    }
    final var expected = props.getProperty("analytics-exporter.version");
    assertThat(expected)
        .as("Maven filtering should have substituted ${project.version}")
        .isNotBlank()
        .doesNotContain("${");

    // then — production code returns the same value
    assertThat(AnalyticsExporterVersion.get()).isEqualTo(expected);
  }
}
