/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainTest {

  @AfterEach
  void tearDown() {
    System.clearProperty("camunda.database.type");
  }

  @Test
  void shouldDetectNoSecondaryStorageModeWhenDatabaseTypeIsNone() {
    // when/then
    assertThat(Main.isNoSecondaryStorageMode("none")).isTrue();
    assertThat(Main.isNoSecondaryStorageMode("NONE")).isTrue();
    assertThat(Main.isNoSecondaryStorageMode("None")).isTrue();
  }

  @Test
  void shouldNotDetectNoSecondaryStorageModeForValidDatabaseTypes() {
    // when/then
    assertThat(Main.isNoSecondaryStorageMode("elasticsearch")).isFalse();
    assertThat(Main.isNoSecondaryStorageMode("opensearch")).isFalse();
    assertThat(Main.isNoSecondaryStorageMode("ELASTICSEARCH")).isFalse();
    assertThat(Main.isNoSecondaryStorageMode("rdbms")).isFalse();
    assertThat(Main.isNoSecondaryStorageMode(null)).isFalse();
    assertThat(Main.isNoSecondaryStorageMode("")).isFalse();
  }

  @Test
  void shouldReadDatabaseTypeFromSystemProperty() {
    // given
    System.setProperty("camunda.database.type", "none");

    // when
    final String databaseType = Main.getDatabaseType();

    // then
    assertThat(databaseType).isEqualTo("none");
  }

  @Test
  void shouldDefaultToElasticsearchWhenNoDatabaseTypeConfigured() {
    // given - no system property set

    // when
    final String databaseType = Main.getDatabaseType();

    // then
    assertThat(databaseType).isEqualTo("elasticsearch");
  }

  @Test
  void shouldReadDatabaseTypeFromSystemPropertyWithDifferentValues() {
    // given
    System.setProperty("camunda.database.type", "opensearch");

    // when
    final String databaseType = Main.getDatabaseType();

    // then
    assertThat(databaseType).isEqualTo("opensearch");
  }
}