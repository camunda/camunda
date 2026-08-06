/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;

@ExcludeAuthorizationCheck
public final class SecretReferenceBatchCreateIncidentsProcessor
    implements TypedRecordProcessor<SecretReferenceRecord> {

  private static final int MAX_BATCH_SIZE = 100;

  private static final String INCIDENT_MESSAGE =
      "Failed to resolve secret '%s' from %s. Ensure the secret exists and the store is available, then resolve the incident to retry.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final SecretReferenceState secretReferenceState;
  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final IncidentState incidentState;
  private final IncidentMetrics incidentMetrics;

  public SecretReferenceBatchCreateIncidentsProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final IncidentMetrics incidentMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    incidentState = processingState.getIncidentState();
    this.incidentMetrics = incidentMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<SecretReferenceRecord> record) {
    final var value = record.getValue();
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();

    // only processed jobs go into the batch event; jobs cut off by the record-batch budget keep
    // their waiting entries and are re-collected into the follow-up command below
    final var processedBatch = newBatch(storeId, secretReference);
    var incidentAppended = false;

    // capacity reserved for the two records written after the incidents: the batch result event
    // (its keys are a subset of the command's, so the command value bounds its size) and a
    // potential follow-up BATCH_CREATE_INCIDENTS command; the buffer absorbs the record metadata
    // not included in the value lengths
    final int followUpRecordsReserve =
        value.getLength()
            + maxNextCommandLength(storeId, secretReference)
            + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;

    for (final long jobKey : value.getJobKeys()) {
      final JobRecord job = jobState.getJob(jobKey);
      if (job == null
          || hasIncident(jobKey)
          || !secretReferenceState.isWaiting(storeId, secretReference, jobKey)) {
        // The job is gone (e.g. its process instance was cancelled), or it already carries an
        // incident from another failed reference, or a reactivation chain drained it before this
        // command was processed. The key list was collected when the command was written, so it
        // can be stale by now. The batch event still cleans up the entry either way.
        processedBatch.addJobKey(jobKey);
        continue;
      }
      final var incidentEvent = buildIncident(jobKey, job, storeId, secretReference);
      // the record batch is empty when this cycle starts, so the check normally passes for the
      // first incident. It can still reject it when the reserve alone exceeds the maximum fragment
      // size, and a cycle that appends nothing drains no entry and re-queues the same job keys, so
      // the first incident is written regardless to keep the chain progressing.
      if (incidentAppended
          && !stateWriter.canWriteEventOfLength(
              incidentEvent.getLength() + followUpRecordsReserve)) {
        break;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
      incidentMetrics.incidentCreated();
      incidentAppended = true;
      processedBatch.addJobKey(jobKey);
    }

    stateWriter.appendFollowUpEvent(
        record.getKey(), SecretReferenceIntent.BATCH_INCIDENTS_CREATED, processedBatch);

    final List<Long> nextBatch = buildNextBatch(storeId, secretReference);

    if (!nextBatch.isEmpty()) {
      final var nextRecord = newBatch(storeId, secretReference);
      nextBatch.forEach(nextRecord::addJobKey);
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(), SecretReferenceIntent.BATCH_CREATE_INCIDENTS, nextRecord);
    }
  }

  /**
   * Each cycle of the chain starts its own record batch. Without this, the follow-up command would
   * be processed in the current batch on the same, already filled result builder, so the budget
   * check would reject incidents based on what earlier cycles wrote. Isolating the cycles keeps the
   * batch bounded by one command's incidents instead of letting the whole chain accumulate into a
   * single batch until it exceeds the log's maximum fragment size.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  private static SecretReferenceRecord newBatch(
      final String storeId, final String secretReference) {
    return new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
  }

  /** Length of the largest possible follow-up command: a full batch of widest-encoded job keys. */
  private static int maxNextCommandLength(final String storeId, final String secretReference) {
    final var maxRecord = newBatch(storeId, secretReference);
    for (int i = 0; i < MAX_BATCH_SIZE; i++) {
      maxRecord.addJobKey(Long.MAX_VALUE);
    }
    return maxRecord.getLength();
  }

  private boolean hasIncident(final long jobKey) {
    return incidentState.getJobIncidentKey(jobKey) != IncidentState.MISSING_INCIDENT;
  }

  /**
   * The {@code camunda.secrets.<name>} syntax carries no store id yet (store selection is tracked
   * under <a href="https://github.com/camunda/camunda/issues/56563">#56563</a>), so every reference
   * names the default store. Naming it in the incident message would tell the reader nothing while
   * it is the only store there is.
   */
  private static String describeStore(final String storeId) {
    return SecretStoreRegistry.DEFAULT_STORE_ID.equals(storeId)
        ? "the configured secret store"
        : "secret store '%s'".formatted(storeId);
  }

  private IncidentRecord buildIncident(
      final long jobKey, final JobRecord job, final String storeId, final String secretReference) {
    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    return new IncidentRecord()
        .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .setErrorMessage(INCIDENT_MESSAGE.formatted(secretReference, describeStore(storeId)))
        .setBpmnProcessId(job.getBpmnProcessIdBuffer())
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setJobKey(jobKey)
        .setVariableScopeKey(job.getElementInstanceKey())
        .setTenantId(job.getTenantId())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());
  }

  private List<Long> buildNextBatch(final String storeId, final String secretReference) {
    final List<Long> nextBatch = new ArrayList<>();
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretReference,
        jobKey -> {
          nextBatch.add(jobKey);
          return nextBatch.size() < MAX_BATCH_SIZE;
        });
    return nextBatch;
  }
}
