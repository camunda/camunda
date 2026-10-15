/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;

public class GroupMembershipChangedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-group";
  private final GroupMembershipChangedHandler underTest =
      new GroupMembershipChangedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.GROUP);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(GroupEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = GroupIntent.class,
      names = {"ENTITY_ADDED", "ENTITY_REMOVED", "ENTITIES_SET"})
  void shouldHandleMembershipChangeRecords(final GroupIntent intent) {
    // given
    final Record<GroupRecordValue> record =
        factory.generateRecordWithIntent(ValueType.GROUP, intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GroupIntent.class,
      names = {"CREATED", "UPDATED", "DELETED"})
  void shouldNotHandleGroupLifecycleRecords(final GroupIntent intent) {
    // given
    final Record<GroupRecordValue> record =
        factory.generateRecordWithIntent(ValueType.GROUP, intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateParentGroupIdAsIdForMembershipChange() {
    // given
    final String groupId = Strings.newRandomValidIdentityId();
    final GroupRecordValue value =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withGroupId(groupId)
            .build();
    final Record<GroupRecordValue> record =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.ENTITY_ADDED).withValue(value));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(GroupIndex.JOIN_RELATION_FACTORY.createParentId(groupId));
  }

  @Test
  void shouldSetLastModifiedAtFromRecordTimestamp() {
    // given
    final long timestamp = Instant.parse("2026-10-03T08:15:00Z").toEpochMilli();
    final Record<GroupRecordValue> record =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.ENTITIES_SET).withTimestamp(timestamp));

    // when
    final GroupEntity entity = new GroupEntity().setId("111");
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getLastModifiedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(entity.getCreatedAt()).isNull();
  }

  @Test
  void shouldPartiallyUpdateOnlyLastModifiedAtOnFlush() throws PersistenceException {
    // given
    final OffsetDateTime lastModifiedAt = OffsetDateTime.parse("2026-10-03T08:15:00Z");
    final GroupEntity entity = new GroupEntity().setId("111").setLastModifiedAt(lastModifiedAt);
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then
    // a full re-index would wipe name, description and the other group fields, since a
    // membership record does not carry them
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> updateFields = ArgumentCaptor.forClass(Map.class);
    verify(mockRequest, times(1)).update(eq(index), eq("111"), updateFields.capture());
    assertThat(updateFields.getValue())
        .containsOnlyKeys(GroupIndex.LAST_MODIFIED_AT)
        .containsEntry(GroupIndex.LAST_MODIFIED_AT, lastModifiedAt);
    verify(mockRequest, never()).add(any(), any());
  }
}
