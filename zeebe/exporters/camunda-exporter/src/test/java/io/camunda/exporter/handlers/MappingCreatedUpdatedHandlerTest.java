/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usermanagement.MappingEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMappingRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class MappingCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-mapping";
  private final MappingCreatedUpdatedHandler underTest =
      new MappingCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.MAPPING);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MappingEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = MappingIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final MappingIntent intent) {
    // given
    final Record<MappingRecordValue> mappingRecord =
        factory.generateRecordWithIntent(ValueType.MAPPING, intent);

    // when - then
    assertThat(underTest.handlesRecord(mappingRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = MappingIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIds(final MappingIntent intent) {
    // given
    final Record<MappingRecordValue> mappingRecord =
        factory.generateRecordWithIntent(ValueType.MAPPING, intent);

    // when
    final var idList = underTest.generateIds(mappingRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(mappingRecord.getValue().getMappingId()));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final MappingRecordValue mappingRecordValue =
        ImmutableMappingRecordValue.builder()
            .from(factory.generateObject(MappingRecordValue.class))
            .withClaimName("updated-claim")
            .withClaimValue("updated-value")
            .withName("updated-name")
            .withMappingId("updated-id")
            .build();

    final Record<MappingRecordValue> mappingRecord =
        factory.generateRecord(
            ValueType.MAPPING,
            r -> r.withIntent(MappingIntent.UPDATED).withValue(mappingRecordValue));

    // when
    final MappingEntity mappingEntity =
        new MappingEntity()
            .setClaimName("old-claim")
            .setClaimValue("old-value")
            .setName("old-name")
            .setId("old-id");
    underTest.updateEntity(mappingRecord, mappingEntity);

    // then
    assertThat(mappingEntity.getClaimName()).isEqualTo("updated-claim");
    assertThat(mappingEntity.getClaimValue()).isEqualTo("updated-value");
    assertThat(mappingEntity.getName()).isEqualTo("updated-name");
    assertThat(mappingEntity.getId()).isEqualTo("updated-id");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final MappingEntity inputEntity = new MappingEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
