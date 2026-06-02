/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.service.WaitStateWriter;
import io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitStateRemoveHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private WaitStateWriter waitStateWriter;

  private WaitStateRemoveHandler<JobRecordValue> handler;

  @BeforeEach
  void setUp() {
    handler = new WaitStateRemoveHandler<>(waitStateWriter, new JobBasedWaitStateTransformer());
  }

  @Test
  void shouldExportJobCompletedRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldExportJobCanceledRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CANCELED));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldNotExportJobCreatedRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldDeleteByRecordKeyOnExport() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withKey(777L).withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when
    handler.export(record);

    // then
    verify(waitStateWriter).delete(777L);
  }
}
