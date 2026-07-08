/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ArchiverPropertiesTest {

  @Test
  void shouldDefaultRolloverBatchSizeWhenArchiveByIdDisabled() {
    final var properties = new ArchiverProperties();
    properties.setArchiveByIdEnabled(false);

    assertThat(properties.getRolloverBatchSize()).isEqualTo(100);
  }

  @Test
  void shouldDefaultToLargerRolloverBatchSizeWhenArchiveByIdEnabled() {
    final var properties = new ArchiverProperties();
    properties.setArchiveByIdEnabled(true);

    assertThat(properties.getRolloverBatchSize()).isEqualTo(500);
  }

  @Test
  void shouldUseExplicitRolloverBatchSizeOverDefault() {
    final var properties = new ArchiverProperties();
    properties.setArchiveByIdEnabled(true);
    properties.setRolloverBatchSize(42);

    assertThat(properties.getRolloverBatchSize()).isEqualTo(42);
  }
}
