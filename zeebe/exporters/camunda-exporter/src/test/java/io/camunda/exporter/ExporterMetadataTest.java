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
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ExporterMetadataTest {
  @Test
  void shouldSerializeToJson() {
    // given
    final var source = new ExporterMetadata(TestObjectMapper.objectMapper());
    final var destination = new ExporterMetadata(TestObjectMapper.objectMapper());
    source.setLastIncidentUpdatePosition(3);
    source.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 4);
    source.setFirstProcessMessageSubscriptionKey(5);
    source.setFirstRootProcessInstanceKey(6);
    destination.setLastIncidentUpdatePosition(-1);

    // when
    final var bytes = source.serialize();
    destination.deserialize(bytes);

    // then
    assertThat(destination.getLastIncidentUpdatePosition()).isEqualTo(3);
    assertThat(destination.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK)).isEqualTo(4);
    assertThat(destination.getFirstProcessMessageSubscriptionKey()).isEqualTo(5);
    assertThat(destination.getFirstRootProcessInstanceKey()).isEqualTo(6);
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

  @Test
  void shouldNotUpdateFirstRootProcessInstanceKeyWithADifferentValue() {
    // given
    final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
    metadata.setFirstRootProcessInstanceKey(5);

    // when
    metadata.setFirstRootProcessInstanceKey(3);

    // then
    assertThat(metadata.getFirstRootProcessInstanceKey()).isEqualTo(5);
  }

  @ParameterizedTest
  @ValueSource(longs = {5, 6})
  void shouldIsKeyAfterFirstRootProcessInstanceKeyReturnTrue(final long key) {
    // given
    final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
    metadata.setFirstRootProcessInstanceKey(5);

    // when - then
    assertThat(metadata.isKeyAfterFirstRootProcessInstanceKey(key)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 4})
  void shouldIsKeyAfterFirstRootProcessInstanceKeyReturnFalse(final long key) {
    // given
    final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
    metadata.setFirstRootProcessInstanceKey(5);

    // when - then
    assertThat(metadata.isKeyAfterFirstRootProcessInstanceKey(key)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, Long.MAX_VALUE})
  void shouldIsKeyAfterFirstRootProcessInstanceKeyReturnFalseWhenMetadataIsUnset(final long key) {
    // given
    final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());

    // when - then
    assertThat(metadata.isKeyAfterFirstRootProcessInstanceKey(key)).isFalse();
  }
}
