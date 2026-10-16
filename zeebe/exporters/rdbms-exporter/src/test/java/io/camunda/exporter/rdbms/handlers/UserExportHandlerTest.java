/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.service.UserWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
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
final class UserExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private UserWriter writer;
  @Captor private ArgumentCaptor<UserDbModel> dbModelCaptor;

  private UserExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UserExportHandler(writer);
  }

  @Test
  void shouldExportExternalIdAndActiveOnCreate() {
    // given
    final Record<UserRecordValue> record = userRecord(UserIntent.CREATED, "ext-1", false);

    // when
    handler.export(record);

    // then
    verify(writer).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().externalId()).isEqualTo("ext-1");
    assertThat(dbModelCaptor.getValue().active()).isFalse();
    assertThat(dbModelCaptor.getValue().attributes()).isEqualTo("{\"department\":\"eng\"}");
    assertThat(dbModelCaptor.getValue().username()).isEqualTo(record.getValue().getUsername());
  }

  @Test
  void shouldExportSourceAndStoreNoneAsNull() {
    // given
    final var owned = userRecord(UserIntent.CREATED, "ext-1", true, "scim-a");
    final var unowned = userRecord(UserIntent.UPDATED, "ext-1", true, "");

    // when
    handler.export(owned);
    handler.export(unowned);

    // then
    verify(writer).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().source()).isEqualTo("scim-a");
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().source()).isNull();
  }

  @Test
  void shouldExportExternalIdAndActiveOnUpdate() {
    // given
    final Record<UserRecordValue> record = userRecord(UserIntent.UPDATED, "ext-2", true);

    // when
    handler.export(record);

    // then
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().externalId()).isEqualTo("ext-2");
    assertThat(dbModelCaptor.getValue().active()).isTrue();
  }

  @Test
  void shouldSetCreatedAtAndLastModifiedAtFromRecordTimestampOnCreate() {
    // given
    final long timestamp = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();
    final Record<UserRecordValue> record = userRecordAt(UserIntent.CREATED, timestamp);
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
    final Record<UserRecordValue> record = userRecordAt(UserIntent.UPDATED, timestamp);

    // when
    handler.export(record);

    // then
    // UPDATE does not write CREATED_AT, so the model must not carry a creation time either
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue().createdAt()).isNull();
    assertThat(dbModelCaptor.getValue().lastModifiedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
  }

  private Record<UserRecordValue> userRecordAt(final UserIntent intent, final long timestamp) {
    return factory.generateRecord(
        ValueType.USER,
        r ->
            r.withIntent(intent)
                .withValue(factory.generateObject(UserRecordValue.class))
                .withTimestamp(timestamp));
  }

  private Record<UserRecordValue> userRecord(
      final UserIntent intent, final String externalId, final boolean active) {
    return userRecord(intent, externalId, active, "");
  }

  private Record<UserRecordValue> userRecord(
      final UserIntent intent, final String externalId, final boolean active, final String source) {
    final UserRecordValue value =
        ImmutableUserRecordValue.builder()
            .from(factory.generateObject(UserRecordValue.class))
            .withExternalId(externalId)
            .withSource(source)
            .withActive(active)
            .withAttributes(Map.of("department", "eng"))
            .build();
    return factory.generateRecord(ValueType.USER, r -> r.withIntent(intent).withValue(value));
  }
}
