/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.EventApplyingStateWriter;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Covers which job resolving a job incident hands out. Uses real state and real event appliers, so
 * the job is checked in the state the {@code RESOLVED} event leaves it in.
 */
@ExtendWith(ProcessingStateExtension.class)
final class IncidentResolveProcessorTest {

  private static final long INCIDENT_KEY = 10L;
  private static final long JOB_KEY = 20L;
  private static final long PROCESS_INSTANCE_KEY = 30L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private StateWriter stateWriter;
  private BpmnJobActivationBehavior jobActivationBehavior;
  private IncidentResolveProcessor processor;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jobState = processingState.getJobState();
    final var eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    stateWriter = new EventApplyingStateWriter(new RecordingTypedEventWriter(), eventAppliers);
    jobActivationBehavior = mock(BpmnJobActivationBehavior.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.rejection()).thenReturn(mock(TypedRejectionWriter.class));
    when(writers.response()).thenReturn(mock(TypedResponseWriter.class));

    final var cslCheck = mock(CslAuthorizationCheck.class);
    when(cslCheck.check(any(), any(), any(), any(Rejection.class)))
        .thenAnswer(invocation -> Either.right(invocation.getArgument(2)));
    final var tenantCheck = mock(CslTenantCheck.class);
    when(tenantCheck.resolveAuthorizedTenants(any()))
        .thenReturn(TenantAccess.allowed(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)));

    processor =
        new IncidentResolveProcessor(
            processingState,
            (TypedRecordProcessor<ProcessInstanceRecord>) mock(TypedRecordProcessor.class),
            (TypedRecordProcessor<UserTaskRecord>) mock(TypedRecordProcessor.class),
            writers,
            jobActivationBehavior,
            cslCheck,
            tenantCheck,
            mock(IncidentMetrics.class));
  }

  @Test
  void shouldPublishJobThatResolvingItsSecretIncidentMadeActivatable() {
    // given - the incident parks the job, as it does for a failed secret resolution
    createJob();
    stateWriter.appendFollowUpEvent(INCIDENT_KEY, IncidentIntent.CREATED, secretIncident());

    // when
    processor.processRecord(resolveCommand());

    // then
    assertThat(jobState.getState(JOB_KEY)).isEqualTo(State.ACTIVATABLE);
    verify(jobActivationBehavior).publishWork(eq(JOB_KEY), any(JobRecord.class));
  }

  @Test
  void shouldNotPublishFailedJobWhenItsSecretIncidentIsResolved() {
    // given - a job that was handed out with its secret incident still open, then failed with
    // retries left and a backoff. Only data written before #67521 was fixed can be in this state
    createJob();
    stateWriter.appendFollowUpEvent(INCIDENT_KEY, IncidentIntent.CREATED, secretIncident());
    stateWriter.appendFollowUpEvent(
        JOB_KEY, JobIntent.FAILED, jobRecord().setRetryBackoff(1_000L).setRecurringTime(5_000L));

    // when
    processor.processRecord(resolveCommand());

    // then - the job stays failed, so only its backoff hands it out again
    assertThat(jobState.getState(JOB_KEY)).isEqualTo(State.FAILED);
    final List<Long> backedOffJobs = new ArrayList<>();
    jobState.findBackedOffJobs(Long.MAX_VALUE, (jobKey, job) -> backedOffJobs.add(jobKey));
    assertThat(backedOffJobs).containsExactly(JOB_KEY);
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(JobRecord.class));
  }

  private void createJob() {
    stateWriter.appendFollowUpEvent(JOB_KEY, JobIntent.CREATED, jobRecord());
  }

  private static JobRecord jobRecord() {
    return new JobRecord()
        .setType("type")
        .setRetries(3)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static IncidentRecord secretIncident() {
    return new IncidentRecord()
        .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .setJobKey(JOB_KEY)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static MockTypedRecord<IncidentRecord> resolveCommand() {
    return new MockTypedRecord<>(
        INCIDENT_KEY,
        new RecordMetadata().recordType(RecordType.COMMAND).intent(IncidentIntent.RESOLVE),
        new IncidentRecord());
  }
}
