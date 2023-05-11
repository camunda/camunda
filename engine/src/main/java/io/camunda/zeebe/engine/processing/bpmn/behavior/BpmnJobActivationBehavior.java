/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.job.JobVariablesCollector;
import io.camunda.zeebe.engine.processing.streamprocessor.JobActivationProperties;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import org.agrona.ExpandableArrayBuffer;

/**
 * A behavior class which allows processors to activate a job. Use this anywhere a job should
 * become activated and processed by a job worker.
 *
 * This behavior class will either push a job on a {@link io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream}
 * or notify job workers that a job of a given type is available for processing. If a <code>JobStream/code>
 * is available for a job with a given type, the job will be pushed on the <code>JobStream/code>. If
 * no <code>JobStream/code> is available for the given job type, a notification is used.
 *
 * Both the job push and the job worker notification are executed through a {@link io.camunda.zeebe.stream.api.SideEffectProducer}.
 */
public class BpmnJobActivationBehavior {
  private final JobStreamer jobStreamer;
  private final JobVariablesCollector jobVariablesCollector;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final KeyGenerator keyGenerator;
  private final JobMetrics jobMetrics;

  public BpmnJobActivationBehavior(
      final JobStreamer jobStreamer,
      final VariableState variableState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobMetrics jobMetrics) {
    this.jobStreamer = jobStreamer;
    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    jobVariablesCollector = new JobVariablesCollector(variableState);
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
  }

  public void publishWork(final long jobKey, final JobRecord jobRecord) {
    final String jobType = jobRecord.getType();
    final Optional<JobStream> optionalJobStream = jobStreamer.streamFor(jobRecord.getTypeBuffer());
    if (optionalJobStream.isPresent()) {
      final JobStream jobStream = optionalJobStream.get();
      final JobActivationProperties properties = jobStream.properties();

      final var deadline = System.currentTimeMillis() + properties.timeout();
      jobRecord.setDeadline(deadline);
      jobRecord.setWorker(properties.worker());
      jobVariablesCollector.setJobVariables(properties.fetchVariables(), jobRecord, jobRecord.getElementInstanceKey());

      // job activation through a job batch
      final JobBatchRecord jobBatchRecord = new JobBatchRecord();
      jobBatchRecord
          .setType(jobType)
          .setTimeout(properties.timeout())
          .setWorker(properties.worker());
      final ValueArray<JobRecord> jobIterator = jobBatchRecord.jobs();
      final ValueArray<LongValue> jobKeyIterator = jobBatchRecord.jobKeys();
      final var jobCopyBuffer = new ExpandableArrayBuffer();
      appendJobToBatch(jobIterator, jobKeyIterator, jobCopyBuffer, jobKey, jobRecord);

      final var jobBatchKey = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATE, jobBatchRecord);
    } else {
      notifyJobAvailable(jobType);
    }
  }

  public void notifyJobAvailableAsSideEffect(final JobRecord jobRecord) {
    final String jobType = jobRecord.getType();
    notifyJobAvailable(jobType);
  }

  private void notifyJobAvailable(final String jobType) {
    sideEffectWriter.appendSideEffect(
        () -> {
          jobStreamer.notifyWorkAvailable(jobType);
          return true;
        });
  }

  private void appendJobToBatch(
      final ValueArray<JobRecord> jobIterator,
      final ValueArray<LongValue> jobKeyIterator,
      final ExpandableArrayBuffer jobCopyBuffer,
      final Long key,
      final JobRecord jobRecord) {
    jobKeyIterator.add().setValue(key);
    final JobRecord arrayValueJob = jobIterator.add();

    // clone job record since buffer is reused during iteration
    jobRecord.write(jobCopyBuffer, 0);
    arrayValueJob.wrap(jobCopyBuffer, 0, jobRecord.getLength());
  }
}
