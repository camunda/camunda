/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class VendorDatabasePropertiesTest {

  @Test
  void shouldLoadOraclePropertiesWithInsertBatchingDisabled() throws IOException {
    // given & when
    final var properties = VendorDatabasePropertiesLoader.load("oracle");

    // then
    assertThat(properties.supportsInsertBatching()).isFalse();
  }

  @Test
  void shouldLoadPostgresqlPropertiesWithInsertBatchingEnabled() throws IOException {
    // given & when
    final var properties = VendorDatabasePropertiesLoader.load("postgresql");

    // then
    assertThat(properties.supportsInsertBatching()).isTrue();
  }
}
