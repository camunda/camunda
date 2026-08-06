/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import java.util.stream.LongStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceBatchCreateIncidentsProcessorTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";
  private static final String BPMN_PROCESS_ID = "process";
  private static final long PROCESS_DEFINITION_KEY = 3L;
  private static final long PROCESS_INSTANCE_KEY = 7L;
  private static final String ELEMENT_ID = "service-task";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private MutableJobState jobState;
  private MutableElementInstanceState elementInstanceState;
  private MutableIncidentState incidentState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private KeyGenerator keyGenerator;
  private IncidentMetrics incidentMetrics;
  private SecretReferenceBatchCreateIncidentsProcessor processor;

  @BeforeEach
  void setUp() {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    elementInstanceState = processingState.getElementInstanceState();
    incidentState = processingState.getIncidentState();

    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    keyGenerator = mock(KeyGenerator.class);
    incidentMetrics = mock(IncidentMetrics.class);
    when(keyGenerator.nextKey()).thenReturn(999L);
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);

    // the real state writer applies events eagerly, so mirror the BATCH_INCIDENTS_CREATED applier:
    // the follow-up batch must be collected from the state the next command would actually see
    doAnswer(
            invocation -> {
              final SecretReferenceRecord event = invocation.getArgument(2);
              event
                  .getJobKeys()
                  .forEach(
                      jobKey ->
                          secretReferenceState.removeWaitingJob(
                              event.getStoreId(), event.getSecretReference(), jobKey));
              return null;
            })
        .when(stateWriter)
        .appendFollowUpEvent(anyLong(), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), any());

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    processor =
        new SecretReferenceBatchCreateIncidentsProcessor(
            writers, keyGenerator, processingState, incidentMetrics);
  }

  private MockTypedRecord<SecretReferenceRecord> command(final SecretReferenceRecord value) {
    return new MockTypedRecord<>(500L, new RecordMetadata(), value);
  }

  private void createWaitingJob(final long jobKey, final long elementInstanceKey) {
    createWaitingJob(jobKey, elementInstanceKey, STORE_ID);
  }

  private void createWaitingJob(
      final long jobKey, final long elementInstanceKey, final String storeId) {
    final var processInstanceRecord =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setElementId(ELEMENT_ID)
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setVersion(1)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    elementInstanceState.newInstance(
        elementInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementId(ELEMENT_ID)
            .setElementInstanceKey(elementInstanceKey)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);

    secretReferenceState.addPendingSecretReference(storeId, SECRET_REF);
    secretReferenceState.addWaitingJob(storeId, SECRET_REF, jobKey);
  }

  @Test
  void shouldWriteBatchIncidentsCreatedEventForCurrentBatch() {
    // given - two jobs waiting on the secret reference, both carried by the command
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - the current batch is written back as a BATCH_INCIDENTS_CREATED event on the same key
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 2L);

    // and - no follow-up command, since state holds only the current batch
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldRaiseIncidentForEachJobInBatch() {
    // given
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - one Incident.CREATED event per job, carrying the job's context
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter, times(2))
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());

    final var incidents = incidentCaptor.getAllValues();
    Assertions.assertThat(incidents).hasSize(2);
    assertIncident(incidents.get(0), 1L, 11L);
    assertIncident(incidents.get(1), 2L, 12L);

    verify(incidentMetrics, times(2)).incidentCreated();
  }

  private void assertIncident(
      final IncidentRecord incident, final long jobKey, final long elementInstanceKey) {
    Assertions.assertThat(incident.getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    Assertions.assertThat(incident.getErrorMessage()).contains(SECRET_REF).contains(STORE_ID);
    Assertions.assertThat(incident.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    Assertions.assertThat(incident.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    Assertions.assertThat(incident.getProcessInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
    Assertions.assertThat(incident.getElementId()).isEqualTo(ELEMENT_ID);
    Assertions.assertThat(incident.getJobKey()).isEqualTo(jobKey);
    Assertions.assertThat(incident.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    Assertions.assertThat(incident.getVariableScopeKey()).isEqualTo(elementInstanceKey);
    Assertions.assertThat(incident.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    Assertions.assertThat(incident.getElementInstancePath())
        .containsExactly(List.of(elementInstanceKey));
  }

  @Test
  void shouldReferenceConfiguredStoreInIncidentMessageWhenStoreIsTheDefaultOne() {
    // given - camunda.secrets.<name> references address the default store until store selection
    // exists, so it is the only store there is
    createWaitingJob(1L, 11L, SecretStoreRegistry.DEFAULT_STORE_ID);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(SecretStoreRegistry.DEFAULT_STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - the message points to the configured store instead of naming an id that tells the
    // reader nothing
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());
    Assertions.assertThat(incidentCaptor.getValue().getErrorMessage())
        .contains(SECRET_REF)
        .contains("the configured secret store")
        .doesNotContain("'" + SecretStoreRegistry.DEFAULT_STORE_ID + "'");
  }

  @Test
  void shouldSkipJobThatNoLongerExists() {
    // given - job 7 has no job record anymore (e.g. its process instance was cancelled while the
    //         waiting entry was still in state)
    createWaitingJob(1L, 11L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(7L);

    // when
    processor.processRecord(command(value));

    // then - only job 1 receives an incident
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter, times(1))
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());
    Assertions.assertThat(incidentCaptor.getValue().getJobKey()).isEqualTo(1L);

    // and - the batch event still carries both keys so state entries are cleaned up
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 7L);
  }

  @Test
  void shouldSkipJobThatAlreadyHasAnIncident() {
    // given - job 1 already has an open incident (e.g. another secret reference of the job failed
    //         in an earlier batch)
    createWaitingJob(1L, 11L);
    incidentState.createIncident(
        55L,
        new IncidentRecord()
            .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .setJobKey(1L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementInstanceKey(11L));
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - no new incident, but the batch event is still written
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), eq(IncidentIntent.CREATED), any());
    verify(stateWriter)
        .appendFollowUpEvent(eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), any());
    verify(incidentMetrics, never()).incidentCreated();
  }

  @Test
  void shouldSkipJobThatIsNoLongerWaitingOnTheSecretReference() {
    // given - job 2 was drained by a reactivation chain that overtook this command, so it is no
    //         longer waiting on the failed reference even though the command still carries it
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    secretReferenceState.removeWaitingJob(STORE_ID, SECRET_REF, 2L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - only the job that is still waiting receives an incident
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter, times(1))
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());
    Assertions.assertThat(incidentCaptor.getValue().getJobKey()).isEqualTo(1L);
    verify(incidentMetrics, times(1)).incidentCreated();

    // and - the batch event still carries both keys so the drain keeps making progress
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 2L);
  }

  @Test
  void shouldWriteFollowUpCommandWhenMoreJobsRemain() {
    // given - three jobs waiting; the command carries jobs 1 and 2 as the current batch, so job 3
    //         is left over for the follow-up
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - a follow-up BATCH_CREATE_INCIDENTS command is written for the remaining job
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_CREATE_INCIDENTS), commandCaptor.capture());
    final var next = commandCaptor.getValue();
    Assertions.assertThat(next.getStoreId()).isEqualTo(STORE_ID);
    Assertions.assertThat(next.getSecretReference()).isEqualTo(SECRET_REF);
    Assertions.assertThat(next.getJobKeys()).containsExactly(3L);
  }

  @Test
  void shouldRequestSeparateBatchForFollowUpCommands() {
    // given - the budget check is only safe for the first incident if every follow-up command
    //         starts its own record batch

    // when / then
    Assertions.assertThat(processor.shouldProcessResultsInSeparateBatches()).isTrue();
  }

  @Test
  void shouldRaiseFirstIncidentEvenWhenReserveDoesNotFitEmptyBatch() {
    // given - the budget rejects every incident, as it does when the reserve alone exceeds the
    //         configured maximum fragment size; jobs 2 and 3 keep their waiting entries (the
    //         batch event only removes the keys it carries)
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(false);
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    createWaitingJob(3L, 13L);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L)
            .addJobKey(3L);

    // when
    processor.processRecord(command(value));

    // then - one incident is still written, so the chain cannot re-queue the same batch forever
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter, times(1))
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());
    Assertions.assertThat(incidentCaptor.getValue().getJobKey()).isEqualTo(1L);

    // and - the batch event carries only the processed job
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L);

    // and - the cut-off jobs are re-batched into the follow-up command
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_CREATE_INCIDENTS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).containsExactly(2L, 3L);
  }

  @Test
  void shouldStopRaisingIncidentsWhenBatchBudgetIsExhaustedMidBatch() {
    // given - capacity for one more incident after the unconditional first one; job 3 keeps its
    //         waiting entry (the batch event only removes the keys it carries)
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true, false);
    createWaitingJob(1L, 11L);
    createWaitingJob(2L, 12L);
    createWaitingJob(3L, 13L);
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L)
            .addJobKey(3L);

    // when
    processor.processRecord(command(value));

    // then - jobs 1 and 2 receive incidents, job 3 is cut off
    final var incidentCaptor = ArgumentCaptor.forClass(IncidentRecord.class);
    verify(stateWriter, times(2))
        .appendFollowUpEvent(eq(999L), eq(IncidentIntent.CREATED), incidentCaptor.capture());
    Assertions.assertThat(incidentCaptor.getAllValues())
        .extracting(IncidentRecord::getJobKey)
        .containsExactly(1L, 2L);

    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_INCIDENTS_CREATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 2L);

    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_CREATE_INCIDENTS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).containsExactly(3L);
  }

  @Test
  void shouldCapFollowUpBatchAtFirstHundredJobsInDrainOrder() {
    // given - 150 jobs remain waiting in state
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    LongStream.rangeClosed(1, 150)
        .forEach(jobKey -> secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey));
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then - the follow-up batch is capped at the first 100 jobs in drain order
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_CREATE_INCIDENTS), commandCaptor.capture());
    final var expectedJobKeys = LongStream.rangeClosed(1, 100).boxed().toList();
    Assertions.assertThat(commandCaptor.getValue().getJobKeys())
        .containsExactlyElementsOf(expectedJobKeys);
  }
}
