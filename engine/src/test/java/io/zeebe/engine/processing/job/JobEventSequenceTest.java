/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.engine.util.RecordToWrite.command;
import static io.zeebe.engine.util.RecordToWrite.event;
import static io.zeebe.protocol.record.intent.JobBatchIntent.ACTIVATE;
import static io.zeebe.protocol.record.intent.JobBatchIntent.ACTIVATED;
import static io.zeebe.protocol.record.intent.JobIntent.CANCEL;
import static io.zeebe.protocol.record.intent.JobIntent.CANCELED;
import static io.zeebe.protocol.record.intent.JobIntent.CREATE;
import static io.zeebe.protocol.record.intent.JobIntent.CREATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class JobEventSequenceTest {

  @Rule public final EngineRule engine = EngineRule.explicitStart();

  @Test
  public void shouldUseJobStateOnCancelCommand() {
    // given
    engine.writeRecords(
        command().job(CREATE),
        event().job(CREATED).causedBy(0),
        command().jobBatch(ACTIVATE),
        command().job(CANCEL));

    // when
    engine.start();

    // then
    final Record<JobRecordValue> canceled =
        RecordingExporter.jobRecords().withIntent(CANCELED).getFirst();

    final var activated =
        RecordingExporter.jobBatchRecords(ACTIVATED).getFirst().getValue().getJobs().get(0);

    assertThat(activated.getDeadline()).isEqualTo(canceled.getValue().getDeadline());

    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == CANCELED)
            .collect(Collectors.toList());
    assertThat(records)
        .extracting(Record::getIntent)
        .containsExactly(CREATE, CREATED, ACTIVATE, CANCEL, ACTIVATED, CANCELED);
  }
}
