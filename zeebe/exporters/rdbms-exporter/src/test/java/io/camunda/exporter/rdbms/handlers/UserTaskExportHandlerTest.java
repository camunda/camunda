/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private UserTaskWriter userTaskWriter;
  @Mock private ExporterEntityCache<Long, CachedProcessEntity> processCache;

  private UserTaskExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UserTaskExportHandler(userTaskWriter, processCache);
  }

  @ParameterizedTest(name = "should be able to export record with intent: {0}")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"CREATED", "ASSIGNED", "UPDATED", "COMPLETED", "CANCELED", "MIGRATED"})
  void shouldExportRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should be able to export record with intent: %s", intent)
        .isTrue();
  }

  @ParameterizedTest(name = "should not export record with unsupported intent: {0}")
  @EnumSource(
      value = UserTaskIntent.class,
      // Exclude the intents that are supported by the handler
      mode = EnumSource.Mode.EXCLUDE,
      names = {"CREATED", "ASSIGNED", "UPDATED", "COMPLETED", "CANCELED", "MIGRATED"})
  void shouldNotExportRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should not be able to export record with unsupported intent: %s", intent)
        // If this assertion fails, it means that the given intent was recently added to
        // `UserTaskExportHandler#EXPORTABLE_INTENTS`.
        // In that case:
        // - Add it to the supported intents list in both this test and the one above
        // - Review whether it needs custom handling in `UserTaskExportHandler#export`:
        //   - If so, add a dedicated handling in `UserTaskExportHandler#export`
        //   - Add a dedicated test for the new intent in this class
        .isFalse();
  }
}
