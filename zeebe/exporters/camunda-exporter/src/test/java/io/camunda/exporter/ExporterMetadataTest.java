/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.test.utils.TestObjectMapper;
import org.junit.jupiter.api.Test;

final class ExporterMetadataTest {
  @Test
  void shouldSerializeToJson() {
    // given
    final var source = new ExporterMetadata(TestObjectMapper.objectMapper());
    final var destination = new ExporterMetadata(TestObjectMapper.objectMapper());
    source.setLastIncidentUpdatePosition(3);
    destination.setLastIncidentUpdatePosition(-1);

    // when
    final var bytes = source.serialize();
    destination.deserialize(bytes);

    // then
    assertThat(destination.getLastIncidentUpdatePosition()).isEqualTo(3);
  }

  @Test
  void shouldNotUpdateIncidentPositionWithALowerValue() {
    // given
    final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
    metadata.setLastIncidentUpdatePosition(3);

    // when
    metadata.setLastIncidentUpdatePosition(2);

    // then
    assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(3);
  }
}
