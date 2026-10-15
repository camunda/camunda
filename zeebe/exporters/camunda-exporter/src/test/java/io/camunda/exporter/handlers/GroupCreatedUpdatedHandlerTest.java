/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.Strings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;

public class GroupCreatedUpdatedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-group";
  private final GroupCreatedUpdatedHandler underTest = new GroupCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.GROUP);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(GroupEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<GroupRecordValue> groupCreatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);
    final Record<GroupRecordValue> groupUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(groupCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(groupUpdatedRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GroupIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIds(final GroupIntent intent) {
    // given
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, intent);
    final String groupId = groupRecord.getValue().getGroupId();
    final String uniqueExporterId = GroupIndex.JOIN_RELATION_FACTORY.createParentId(groupId);

    // when
    final var idList = underTest.generateIds(groupRecord);

    // then
    assertThat(idList).containsExactly(uniqueExporterId);
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
    final var recordKey = 123L;
    final var groupId = Strings.newRandomValidIdentityId();

    final GroupRecordValue groupRecordValue =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withName("updated-foo")
            .withDescription("updated-bar")
            .withExternalId("ext-1")
            .withAttributes(Map.of("cc", "42"))
            .withGroupId(groupId)
            .withGroupKey(recordKey)
            .build();

    final Record<GroupRecordValue> groupRecord =
        factory.generateRecord(
            ValueType.GROUP,
            r -> r.withIntent(GroupIntent.CREATED).withValue(groupRecordValue).withKey(recordKey));

    // when
    final GroupEntity groupEntity =
        new GroupEntity().setName("foo").setGroupId(groupId).setDescription("bar");
    underTest.updateEntity(groupRecord, groupEntity);

    // then
    assertThat(groupEntity.getName()).isEqualTo("updated-foo");
    assertThat(groupEntity.getDescription()).isEqualTo("updated-bar");
    assertThat(groupEntity.getExternalId()).isEqualTo("ext-1");
    assertThat(groupEntity.getAttributes()).isEqualTo("{\"cc\":\"42\"}");
  }

  @Test
  void shouldSetCreatedAndLastModifiedAtFromRecordTimestampOnCreated() {
    // given
    final long timestamp = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.CREATED).withTimestamp(timestamp));

    // when
    final GroupEntity groupEntity = new GroupEntity().setId("111");
    underTest.updateEntity(groupRecord, groupEntity);

    // then
    final var expected = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
    assertThat(groupEntity.getCreatedAt()).isEqualTo(expected);
    assertThat(groupEntity.getLastModifiedAt()).isEqualTo(expected);
  }

  @Test
  void shouldOnlySetLastModifiedAtFromRecordTimestampOnUpdated() {
    // given
    final long timestamp = Instant.parse("2026-10-02T12:30:00Z").toEpochMilli();
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.UPDATED).withTimestamp(timestamp));

    // when
    final GroupEntity groupEntity = new GroupEntity().setId("111");
    underTest.updateEntity(groupRecord, groupEntity);

    // then
    assertThat(groupEntity.getCreatedAt()).isNull();
    assertThat(groupEntity.getLastModifiedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
  }

  @Test
  void shouldUpsertEntityOnFlushWithoutOverwritingCreatedAt() throws PersistenceException {
    // given
    final OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    final OffsetDateTime lastModifiedAt = OffsetDateTime.parse("2026-10-02T12:30:00Z");
    final GroupEntity inputEntity =
        new GroupEntity()
            .setId("111")
            .setGroupId("group-1")
            .setName("name")
            .setCreatedAt(createdAt)
            .setLastModifiedAt(lastModifiedAt);
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    // createdAt is only used by the insert half of the upsert; an update must not carry it,
    // otherwise an UPDATED record would reset the creation time
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> updateFields = ArgumentCaptor.forClass(Map.class);
    verify(mockRequest, times(1))
        .upsert(eq(index), eq("111"), eq(inputEntity), updateFields.capture());
    assertThat(updateFields.getValue())
        .containsEntry(GroupIndex.GROUP_ID, "group-1")
        .containsEntry(GroupIndex.NAME, "name")
        .containsEntry(GroupIndex.LAST_MODIFIED_AT, lastModifiedAt)
        .doesNotContainKey(GroupIndex.CREATED_AT);
  }
}
