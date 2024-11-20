/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ExporterMetadataTest {
  @Test
  void shouldSerializeToJson() {
    // given
    final var source = new ExporterMetadata();
    final var destination = new ExporterMetadata();
    source.setLastIncidentUpdatePosition(3);
    destination.setLastIncidentUpdatePosition(-1);

    // when
    final var bytes = source.serialize();
    destination.deserialize(bytes);

    // then
    assertThat(destination.getLastIncidentUpdatePosition()).isEqualTo(3);
  }
}
