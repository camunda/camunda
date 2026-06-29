/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ZeebeRecordListenerTest {

  @Test
  void shouldDispatchToMatchingTypedHandlerAndCatchAll() {
    // given
    final AtomicReference<JobRecordValue> matchedJob = new AtomicReference<>();
    final AtomicInteger variableCalls = new AtomicInteger();
    final AtomicReference<Record<?>> catchAll = new AtomicReference<>();

    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.on(JobRecordValue.class, (record, job) -> matchedJob.set(job));
    dispatcher.on(VariableRecordValue.class, (record, variable) -> variableCalls.incrementAndGet());
    dispatcher.onAny(catchAll::set);

    final Record<?> record = jobRecord("payment");

    // when
    dispatcher.dispatch(record);

    // then — the JOB handler and the catch-all fire; the VARIABLE handler does not
    assertThat(matchedJob.get()).isNotNull();
    assertThat(matchedJob.get().getType()).isEqualTo("payment");
    assertThat(catchAll.get()).isSameAs(record);
    assertThat(variableCalls.get()).isZero();
  }

  @Test
  void shouldNotRequireACatchAllHandler() {
    // given
    final AtomicReference<JobRecordValue> matchedJob = new AtomicReference<>();
    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.on(JobRecordValue.class, (record, job) -> matchedJob.set(job));

    // when / then — dispatch with no catch-all registered does not throw
    dispatcher.dispatch(jobRecord("refund"));
    assertThat(matchedJob.get().getType()).isEqualTo("refund");
  }

  private static Record<?> jobRecord(final String type) {
    final JobRecord value = new JobRecord().setType(type);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    return new CopiedRecord<>(value, metadata, 1L, 1, 1L, -1L, 1L);
  }
}
