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
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PostImporterQueueFromProcessInstanceMigrationHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-post-import-queue";

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);

  private final PostImporterQueueFromProcessInstanceMigrationHandler underTest =
      new PostImporterQueueFromProcessInstanceMigrationHandler(indexName, processCache, true);

  @Test
  void shouldHandleProcessInstanceMigrationValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MIGRATION);
  }

  @Test
  void shouldReturnPostImporterQueueEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(PostImporterQueueEntity.class);
  }

  @Test
  void shouldNotHandleRecordWithMigrateIntent() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCacheWithUserTaskTransition(sourceKey, targetKey);
    final Record<ProcessInstanceMigrationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MIGRATION,
            r -> r.withIntent(ProcessInstanceMigrationIntent.MIGRATE));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenSkipFlagIsDisabled() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCacheWithUserTaskTransition(sourceKey, targetKey);
    final var handlerWithFlagOff =
        new PostImporterQueueFromProcessInstanceMigrationHandler(indexName, processCache, false);
    final var record = buildMigratedRecord(sourceKey, targetKey, /* processInstanceKey= */ 100L);

    // when - then
    assertThat(handlerWithFlagOff.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenSourceProcessHasUserTasks() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCache(sourceKey, /* hasUserTasks= */ true);
    setupCache(targetKey, /* hasUserTasks= */ true);
    final var record = buildMigratedRecord(sourceKey, targetKey, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenTargetProcessHasNoUserTasks() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCache(sourceKey, /* hasUserTasks= */ false);
    setupCache(targetKey, /* hasUserTasks= */ false);
    final var record = buildMigratedRecord(sourceKey, targetKey, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordOnSourceCacheMiss() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    org.mockito.Mockito.when(processCache.get(sourceKey)).thenReturn(Optional.empty());
    setupCache(targetKey, /* hasUserTasks= */ true);
    final var record = buildMigratedRecord(sourceKey, targetKey, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordOnTargetCacheMiss() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCache(sourceKey, /* hasUserTasks= */ false);
    org.mockito.Mockito.when(processCache.get(targetKey)).thenReturn(Optional.empty());
    final var record = buildMigratedRecord(sourceKey, targetKey, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldHandleRecordWhenSourceHasNoUserTasksAndTargetDoes() {
    // given
    final long sourceKey = 10L;
    final long targetKey = 20L;
    setupCacheWithUserTaskTransition(sourceKey, targetKey);
    final var record = buildMigratedRecord(sourceKey, targetKey, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIdFromProcessInstanceKey() {
    // given
    final long processInstanceKey = 999L;
    final var value =
        ImmutableProcessInstanceMigrationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceMigrationRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();
    final Record<ProcessInstanceMigrationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MIGRATION,
            r -> r.withIntent(ProcessInstanceMigrationIntent.MIGRATED).withValue(value));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(processInstanceKey));
  }

  @Test
  void shouldCreateNewEntityWithId() {
    // when
    final var entity = underTest.createNewEntity("42");

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("42");
  }

  @Test
  void shouldFlushEntityViaBatchRequestAdd() {
    // given
    final PostImporterQueueEntity entity = new PostImporterQueueEntity().setId("1");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, entity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long processInstanceKey = 100L;
    final long rootProcessInstanceKey = 50L;
    final int partitionId = 3;
    final long position = 77L;

    final var value =
        ImmutableProcessInstanceMigrationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceMigrationRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .build();
    final Record<ProcessInstanceMigrationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MIGRATION,
            r ->
                r.withIntent(ProcessInstanceMigrationIntent.MIGRATED)
                    .withValue(value)
                    .withPartitionId(partitionId)
                    .withPosition(position));

    // when
    final var entity = new PostImporterQueueEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(entity.getKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(entity.getActionType()).isEqualTo(PostImporterActionType.PROCESS_INSTANCE_MIGRATION);
    assertThat(entity.getIntent()).isEqualTo(ProcessInstanceMigrationIntent.MIGRATED.name());
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getPosition()).isEqualTo(position);
    assertThat(entity.getCreationTime()).isNotNull();
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
    // given
    final var value =
        ImmutableProcessInstanceMigrationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceMigrationRecordValue.class))
            .withRootProcessInstanceKey(-1L)
            .build();
    final Record<ProcessInstanceMigrationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MIGRATION,
            r -> r.withIntent(ProcessInstanceMigrationIntent.MIGRATED).withValue(value));

    // when
    final var entity = new PostImporterQueueEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isNull();
  }

  // --- helpers ---

  private Record<ProcessInstanceMigrationRecordValue> buildMigratedRecord(
      final long sourceDefinitionKey,
      final long targetDefinitionKey,
      final long processInstanceKey) {
    final var value =
        ImmutableProcessInstanceMigrationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceMigrationRecordValue.class))
            .withProcessDefinitionKey(sourceDefinitionKey)
            .withTargetProcessDefinitionKey(targetDefinitionKey)
            .withProcessInstanceKey(processInstanceKey)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        r -> r.withIntent(ProcessInstanceMigrationIntent.MIGRATED).withValue(value));
  }

  private void setupCache(final long processDefinitionKey, final boolean hasUserTasks) {
    final var entity = new CachedProcessEntity(null, 0, null, null, null, hasUserTasks, null);
    org.mockito.Mockito.when(processCache.get(processDefinitionKey))
        .thenReturn(Optional.of(entity));
  }

  private void setupCacheWithUserTaskTransition(final long sourceKey, final long targetKey) {
    setupCache(sourceKey, /* hasUserTasks= */ false);
    setupCache(targetKey, /* hasUserTasks= */ true);
  }
}
