/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.OversizedJob;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.Preparation;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

@ExcludeAuthorizationCheck
public final class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

  /** Scratch copy of the batch that carries the injected secret values to the response only. */
  private final JobBatchRecord responseValue = new JobBatchRecord();

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final JobBatchCollector jobBatchCollector;
  private final KeyGenerator keyGenerator;
  private final JobProcessingMetrics jobMetrics;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final CslAuthorizationCheck cslCheck;
  private final IncidentMetrics incidentMetrics;
  private final JobSecretInjector jobSecretInjector;

  public JobBatchActivateProcessor(
      final Writers writers,
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final CslAuthorizationCheck cslCheck,
      final InstantSource clock,
      final IncidentMetrics incidentMetrics,
      final SecretResolver secretResolver) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;
    jobSecretInjector = new JobSecretInjector(secretResolver);
    jobBatchCollector =
        new JobBatchCollector(
            state, stateWriter::canWriteEventOfLength, cslCheck, clock, jobMetrics);

    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    this.incidentMetrics = incidentMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    final var authorizedTenantIds = cslCheck.resolveAuthorizedTenants(record.getAuthorizations());
    final var value = record.getValue();

    final var validationResult = validateRequest(record, authorizedTenantIds);
    if (validationResult.isLeft()) {
      rejectCommand(record, validationResult.getLeft());
      return;
    }

    final var tenantIds = determineTenantIds(value, authorizedTenantIds);
    activateJobs(record, tenantIds);
  }

  private List<String> determineTenantIds(
      final JobBatchRecord value, final AuthorizedTenants authorizedTenantIds) {
    if (value.getTenantFilter() == TenantFilter.ASSIGNED) {
      return authorizedTenantIds.getAuthorizedTenantIds();
    }

    final var providedTenantIds = value.getTenantIds();
    return providedTenantIds.isEmpty()
        ? List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        : providedTenantIds;
  }

  private Either<Rejection, Void> validateRequest(
      final TypedRecord<JobBatchRecord> record, final AuthorizedTenants authorizedTenantIds) {
    final var value = record.getValue();

    // Skip tenant authorization check when using ASSIGNED filter
    if (TenantFilter.PROVIDED.equals(value.getTenantFilter())) {
      final var tenantAuthResult = validateTenantAuthorization(value, authorizedTenantIds);
      if (tenantAuthResult.isLeft()) {
        return tenantAuthResult;
      }
    }

    return validateCommandFields(value);
  }

  private Either<Rejection, Void> validateTenantAuthorization(
      final JobBatchRecord record, final AuthorizedTenants authorizedTenantIds) {
    final var tenantIds = record.getTenantIds();

    if (!authorizedTenantIds.isAuthorizedForTenantIds(tenantIds)) {
      return Either.left(
          new Rejection(
              RejectionType.UNAUTHORIZED,
              "Expected to activate job batch for tenants '%s', but user is not authorized. Authorized tenants are '%s'"
                  .formatted(tenantIds, authorizedTenantIds.getAuthorizedTenantIds())));
    }

    return Either.right(null);
  }

  private Either<Rejection, Void> validateCommandFields(final JobBatchRecord record) {
    if (record.getMaxJobsToActivate() <= 0) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to activate job batch with max jobs to activate to be greater than zero, but it was '%d'"
                  .formatted(record.getMaxJobsToActivate())));
    }
    if (record.getTimeout() <= 0) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to activate job batch with timeout to be greater than zero, but it was '%d'"
                  .formatted(record.getTimeout())));
    }
    if (record.getTypeBuffer().capacity() <= 0) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to activate job batch with type to be present, but it was blank"));
    }
    return Either.right(null);
  }

  private void activateJobs(
      final TypedRecord<JobBatchRecord> record, final List<String> tenantIds) {
    final JobBatchRecord value = record.getValue();
    final long jobBatchKey = keyGenerator.nextKey();

    jobBatchCollector
        .collectJobs(record, tenantIds)
        .ifLeft(
            largeJob ->
                raiseIncidentJobTooLargeForMessageSize(
                    largeJob.key(), largeJob.jobRecord(), largeJob.expectedEventLength()));

    activateJobBatch(record, value, jobBatchKey);
  }

  private void rejectCommand(final TypedRecord<JobBatchRecord> record, final Rejection rejection) {
    rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(record, rejection.type(), rejection.reason());
  }

  private void activateJobBatch(
      final TypedRecord<JobBatchRecord> record,
      final JobBatchRecord value,
      final long jobBatchKey) {
    // jobs whose secret references are not all cached must not be activated: remove them from the
    // batch before the ACTIVATED event is appended
    final var preparation = jobSecretInjector.removeJobsWithUncachedSecrets(value);
    // building the response can drop further jobs from the batch (those whose injected secret
    // values would exceed the max message size), so it must also happen before the event
    final var response = responseValueFor(record, value, preparation);
    // append (and apply to state) the ACTIVATED event with the unresolved placeholders
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    // request the background resolution of the uncached secrets of the removed jobs; the applier
    // also marks those jobs not activatable so a long poll does not collect them again right away
    requestSecretResolution(preparation);
    responseWriter.writeAcceptedResponseOnCommand(
        jobBatchKey, JobBatchIntent.ACTIVATED, response, record);
    countActivatedJobs(value);
  }

  /**
   * Writes one {@code SecretReference.RESOLUTION_REQUESTED} event per uncached reference of the
   * removed jobs, carrying the keys of the jobs that await it, so the background task resolves it
   * into the cache and the jobs are reactivated once every reference they wait for is resolved.
   */
  private void requestSecretResolution(final Preparation preparation) {
    preparation
        .missingSecrets()
        .forEach(
            (reference, jobKeys) -> {
              final var request =
                  new SecretReferenceRecord()
                      .setStoreId(reference.storeId())
                      .setSecretReference(reference.secretReference());
              for (final long jobKey : jobKeys) {
                request.addJobKey(jobKey);
              }
              stateWriter.appendFollowUpEvent(
                  keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, request);
            });
  }

  /**
   * Returns the batch value to write to the activation response. Secret values must reach the
   * worker via the response only, never the persisted event, state, exported records, or logs: the
   * values are injected into a copy of the batch, so the command value always keeps the
   * placeholders.
   *
   * <p>Jobs whose injected values would exceed the max message size are dropped from the response
   * and the command value alike, to be activated in a later batch; a job whose values can never fit
   * gets a message-size incident instead, like a job that is too large without secrets.
   */
  private JobBatchRecord responseValueFor(
      final TypedRecord<JobBatchRecord> record,
      final JobBatchRecord value,
      final Preparation preparation) {
    if (!record.hasRequestMetadata() || preparation.pendingJobs().isEmpty()) {
      return value;
    }
    responseValue.wrap(value);
    jobSecretInjector
        .injectSecretValues(responseValue, value, preparation)
        .ifPresent(this::raiseIncidentJobSecretValuesTooLargeForMessageSize);
    return responseValue;
  }

  /** Counts the activated-job metrics from the batch as it was actually activated. */
  private void countActivatedJobs(final JobBatchRecord value) {
    final Map<JobKind, Integer> countPerJobKind = new EnumMap<>(JobKind.class);
    for (final JobRecord job : value.jobs()) {
      countPerJobKind.merge(job.getJobKind(), 1, Integer::sum);
    }
    countPerJobKind.forEach(
        (jobKind, count) ->
            jobMetrics.countJobEvent(JobAction.ACTIVATED, jobKind, value.getType(), count));
  }

  private void raiseIncidentJobTooLargeForMessageSize(
      final long jobKey, final JobRecord job, final int expectedJobRecordSize) {
    final String jobSize = ByteValue.prettyPrint(expectedJobRecordSize);
    raiseMessageSizeExceededIncident(
        jobKey,
        job,
        String.format(
            "The job with key '%s' can not be activated, because with %s it is larger than the configured message size (per default is 4 MB). "
                + "Try to reduce the size by reducing the number of fetched variables or modifying the variable values.",
            jobKey, jobSize));
  }

  private void raiseIncidentJobSecretValuesTooLargeForMessageSize(final OversizedJob oversized) {
    final String growth = ByteValue.prettyPrint(oversized.growth());
    raiseMessageSizeExceededIncident(
        oversized.jobKey(),
        oversized.job(),
        String.format(
            "The job with key '%s' can not be activated, because injecting its secret values would grow the activation batch by %s, "
                + "more than any batch can grow without exceeding the configured message size (per default is 4 MB). "
                + "Try to reduce the size of the secret values or of the job variables.",
            oversized.jobKey(), growth));
  }

  private void raiseMessageSizeExceededIncident(
      final long jobKey, final JobRecord job, final String message) {
    final DirectBuffer incidentMessage = wrapString(message);

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    final var incidentEvent =
        new IncidentRecord()
            .setErrorType(ErrorType.MESSAGE_SIZE_EXCEEDED)
            .setErrorMessage(incidentMessage)
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setJobKey(jobKey)
            .setTenantId(job.getTenantId())
            .setVariableScopeKey(job.getElementInstanceKey())
            .setElementInstancePath(treePathProperties.elementInstancePath())
            .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
            .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
    incidentMetrics.incidentCreated();
  }
}
