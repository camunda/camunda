/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.service.GroupWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class GroupExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private GroupWriter writer;
  @Captor private ArgumentCaptor<GroupDbModel> dbModelCaptor;

  private GroupExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GroupExportHandler(writer);
  }

  @Test
  void shouldExportExternalIdOnCreate() {
    // given
    final Record<GroupRecordValue> record = groupRecord(GroupIntent.CREATED, "ext-1");

    // when
    handler.export(record);

    // then
    verify(writer).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().externalId()).isEqualTo("ext-1");
    assertThat(dbModelCaptor.getValue().attributes()).isEqualTo("{\"cc\":\"42\"}");
    assertThat(dbModelCaptor.getValue().groupId()).isEqualTo(record.getValue().getGroupId());
  }

  @Test
  void shouldExportExternalIdOnUpdate() {
    // given
    final Record<GroupRecordValue> record = groupRecord(GroupIntent.UPDATED, "ext-2");

    // when
    handler.export(record);

    // then
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().externalId()).isEqualTo("ext-2");
  }

  @Test
  void shouldSetCreatedAtAndLastModifiedAtFromRecordTimestampOnCreate() {
    // given
    final long timestamp = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();
    final Record<GroupRecordValue> record = groupRecordAt(GroupIntent.CREATED, timestamp);
    final var expected = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);

    // when
    handler.export(record);

    // then
    verify(writer).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().createdAt()).isEqualTo(expected);
    assertThat(dbModelCaptor.getValue().lastModifiedAt()).isEqualTo(expected);
  }

  @Test
  void shouldOnlySetLastModifiedAtFromRecordTimestampOnUpdate() {
    // given
    final long timestamp = Instant.parse("2026-10-02T12:30:00Z").toEpochMilli();
    final Record<GroupRecordValue> record = groupRecordAt(GroupIntent.UPDATED, timestamp);

    // when
    handler.export(record);

    // then
    // UPDATE does not write CREATED_AT, so the model must not carry a creation time either
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().createdAt()).isNull();
    assertThat(dbModelCaptor.getValue().lastModifiedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
  }

  @Test
  void shouldBumpLastModifiedAtOnEntityAdded() {
    // given
    final long timestamp = Instant.parse("2026-10-03T08:15:00Z").toEpochMilli();
    final Record<GroupRecordValue> record = groupRecordAt(GroupIntent.ENTITY_ADDED, timestamp);
    final var expected = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);

    // when
    handler.export(record);

    // then
    verify(writer).addMember(any());
    verify(writer).updateLastModifiedAt(record.getValue().getGroupId(), expected);
  }

  @Test
  void shouldBumpLastModifiedAtOnEntityRemoved() {
    // given
    final long timestamp = Instant.parse("2026-10-03T08:20:00Z").toEpochMilli();
    final Record<GroupRecordValue> record = groupRecordAt(GroupIntent.ENTITY_REMOVED, timestamp);
    final var expected = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);

    // when
    handler.export(record);

    // then
    verify(writer).removeMember(any());
    verify(writer).updateLastModifiedAt(record.getValue().getGroupId(), expected);
  }

  @Test
  void shouldBumpLastModifiedAtOnEntitiesSetWithoutTouchingMembers() {
    // given
    final long timestamp = Instant.parse("2026-10-03T08:25:00Z").toEpochMilli();
    final Record<GroupRecordValue> record = groupRecordAt(GroupIntent.ENTITIES_SET, timestamp);
    final var expected = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);

    // when
    handler.export(record);

    // then
    // the individual membership changes are exported as ENTITY_ADDED / ENTITY_REMOVED, so
    // ENTITIES_SET only has to bump the timestamp
    verify(writer).updateLastModifiedAt(record.getValue().getGroupId(), expected);
    verify(writer, never()).addMember(any());
    verify(writer, never()).removeMember(any());
  }

  private Record<GroupRecordValue> groupRecordAt(final GroupIntent intent, final long timestamp) {
    return factory.generateRecord(
        ValueType.GROUP,
        r ->
            r.withIntent(intent)
                .withValue(factory.generateObject(GroupRecordValue.class))
                .withTimestamp(timestamp));
  }

  private Record<GroupRecordValue> groupRecord(final GroupIntent intent, final String externalId) {
    final GroupRecordValue value =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withExternalId(externalId)
            .withAttributes(Map.of("cc", "42"))
            .build();
    return factory.generateRecord(ValueType.GROUP, r -> r.withIntent(intent).withValue(value));
  }
}
