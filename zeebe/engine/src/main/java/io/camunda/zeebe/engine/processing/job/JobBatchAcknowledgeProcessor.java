/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Confirms that the gateway received a JobBatch activation response. Idempotent: a missing pending
 * delivery is treated as already settled.
 */
@ExcludeAuthorizationCheck
public final class JobBatchAcknowledgeProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private final StateWriter stateWriter;

  public JobBatchAcknowledgeProcessor(final Writers writers) {
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    // Always write ACKNOWLEDGED so retries are safe; the applier no-ops when already cleared.
    stateWriter.appendFollowUpEvent(
        record.getKey(), JobBatchIntent.ACKNOWLEDGED, record.getValue());
  }
}
