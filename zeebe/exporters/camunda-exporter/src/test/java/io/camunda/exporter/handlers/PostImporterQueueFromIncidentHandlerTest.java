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

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class PostImporterQueueFromIncidentHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-post-import-queue";
  private final PostImporterQueueFromIncidentHandler underTest =
      new PostImporterQueueFromIncidentHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(PostImporterQueueEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @Test
  void shouldGenerateIdsForCreated() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue decisionRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.CREATED)
                    .withValue(decisionRecordValue)
                    .withKey(expectedId));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(expectedId + "-" + IncidentIntent.CREATED);
  }

  @Test
  void shouldGenerateIdsForMigrated() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue decisionRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> decisionRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.MIGRATED)
                    .withValue(decisionRecordValue)
                    .withKey(expectedId));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(expectedId + "-" + IncidentIntent.CREATED);
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
  void shouldAddEntityOnFlush() {
    // given
    final PostImporterQueueEntity inputEntity = new PostImporterQueueEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(IncidentIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey));

    // when
    final PostImporterQueueEntity postImporterQueueEntity = new PostImporterQueueEntity();
    underTest.updateEntity(incidentRecord, postImporterQueueEntity);

    // then
    assertThat(postImporterQueueEntity.getId()).isEqualTo(recordKey + "-" + IncidentIntent.CREATED);
    assertThat(postImporterQueueEntity.getKey()).isEqualTo(123L);
    assertThat(postImporterQueueEntity.getIntent()).isEqualTo(IncidentIntent.CREATED.name());
    assertThat(postImporterQueueEntity.getActionType()).isEqualTo(PostImporterActionType.INCIDENT);
    assertThat(postImporterQueueEntity.getCreationTime()).isNotNull();
    assertThat(postImporterQueueEntity.getPartitionId()).isEqualTo(incidentRecord.getPartitionId());
    assertThat(postImporterQueueEntity.getPosition()).isEqualTo(incidentRecord.getPosition());
    assertThat(postImporterQueueEntity.getProcessInstanceKey())
        .isEqualTo(incidentRecordValue.getProcessInstanceKey());
  }

  @Test
  void shouldUpdateMigratedIntentAsCreatedFromRecord() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.DECISION,
            r ->
                r.withIntent(IncidentIntent.MIGRATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey));

    // when
    final PostImporterQueueEntity postImporterQueueEntity = new PostImporterQueueEntity();
    underTest.updateEntity(incidentRecord, postImporterQueueEntity);

    // then
    assertThat(postImporterQueueEntity.getId()).isEqualTo(recordKey + "-" + IncidentIntent.CREATED);
    assertThat(postImporterQueueEntity.getKey()).isEqualTo(incidentRecord.getKey());
    assertThat(postImporterQueueEntity.getIntent()).isEqualTo(IncidentIntent.CREATED.name());
  }
}
