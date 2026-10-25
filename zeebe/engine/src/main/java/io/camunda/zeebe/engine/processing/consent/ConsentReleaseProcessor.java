/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * Hands out the jobs that a grant released, in batches: the parked jobs of the grant's subject,
 * connection and operation that now pass the gate. A job the grant does not cover stays parked, for
 * example one that someone else asked for and no rule admits.
 */
public final class ConsentReleaseProcessor implements TypedRecordProcessor<ConsentRecord> {

  private static final int MAX_BATCH_SIZE = 100;

  private final ConsentState consentState;
  private final JobState jobState;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final ConsentGate consentGate;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;

  public ConsentReleaseProcessor(
      final ProcessingState state,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    consentState = state.getConsentState();
    jobState = state.getJobState();
    this.jobActivationBehavior = jobActivationBehavior;
    consentGate = jobActivationBehavior.consentGate();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<ConsentRecord> command) {
    final ConsentRecord value = command.getValue();
    final List<Long> visited = new ArrayList<>();
    final ConsentRecord released =
        new ConsentRecord()
            .setSubject(value.getSubject())
            .setConnection(value.getConnection())
            .setOperation(value.getOperation())
            .setConsentKey(value.getConsentKey());
    final List<Long> toHandOut = new ArrayList<>();
    consentState.visitParkedJobs(
        value.getSubject(),
        value.getConnection(),
        value.getOperation(),
        value.getJobKey(),
        jobKey -> {
          visited.add(jobKey);
          final var job = jobState.getJob(jobKey);
          if (job == null) {
            // the job is gone; the release drops its stale index entry
            released.addJobKey(jobKey);
          } else if (!consentGate.mustPark(job)) {
            released.addJobKey(jobKey);
            toHandOut.add(jobKey);
          }
          return visited.size() < MAX_BATCH_SIZE;
        });

    if (!released.getJobKeys().isEmpty()) {
      stateWriter.appendFollowUpEvent(command.getKey(), ConsentIntent.RELEASED, released);
      for (final long jobKey : toHandOut) {
        final var job = jobState.getJob(jobKey);
        if (job != null) {
          jobActivationBehavior.publishWork(jobKey, job);
        }
      }
    }

    if (visited.size() >= MAX_BATCH_SIZE) {
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(),
          ConsentIntent.RELEASE,
          new ConsentRecord()
              .setSubject(value.getSubject())
              .setConnection(value.getConnection())
              .setOperation(value.getOperation())
              .setConsentKey(value.getConsentKey())
              .setJobKey(visited.getLast()));
    }
  }

  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }
}
