/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.DroppedJob;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.FailedInjectionJob;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.OversizedJob;
import io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
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
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final JobSecretInjector jobSecretInjector;
  private final SecretResolutionScheduler secretResolutionScheduler;
  private final BpmnIncidentBehavior incidentBehavior;

  public JobBatchActivateProcessor(
      final Writers writers,
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final InstantSource clock,
      final BpmnIncidentBehavior incidentBehavior,
      final SecretStoreRegistry secretStoreRegistry,
      final SecretResolutionScheduler secretResolutionScheduler) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    jobSecretInjector = new JobSecretInjector(secretStoreRegistry);
    this.secretResolutionScheduler = secretResolutionScheduler;
    jobBatchCollector =
        new JobBatchCollector(
            state,
            stateWriter::canWriteEventOfLength,
            cslCheck,
            clock,
            jobMetrics,
            jobSecretInjector);

    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    this.incidentBehavior = incidentBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    final var authorizedTenantIds =
        tenantCheck.resolveAuthorizedTenants(record.getAuthorizations());
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
      final JobBatchRecord value, final TenantAccess authorizedTenantIds) {
    if (value.getTenantFilter() == TenantFilter.ASSIGNED) {
      return authorizedTenantIds.tenantIds();
    }

    return resolveProvidedTenantIds(value);
  }

  // An empty PROVIDED tenant-ID list is treated as "the default tenant" both here and in
  // validateTenantAuthorization, so the tenant-authorization check and the tenants actually used
  // for activation can never diverge (isAuthorizedForTenantIds([]) is vacuously true, so checking
  // the raw empty list would silently authorize a caller not actually assigned to the default
  // tenant).
  private List<String> resolveProvidedTenantIds(final JobBatchRecord value) {
    final var providedTenantIds = value.getTenantIds();
    return providedTenantIds.isEmpty()
        ? List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        : providedTenantIds;
  }

  private Either<Rejection, Void> validateRequest(
      final TypedRecord<JobBatchRecord> record, final TenantAccess authorizedTenantIds) {
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
      final JobBatchRecord value, final TenantAccess authorizedTenantIds) {
    final var tenantIds = resolveProvidedTenantIds(value);
    return tenantCheck.checkTenantsRequiringPrincipal(
        tenantIds,
        authorizedTenantIds,
        null,
        () ->
            new Rejection(
                RejectionType.UNAUTHORIZED,
                "Expected to activate job batch for tenants '%s', but user is not authorized. Authorized tenants are '%s'"
                    .formatted(tenantIds, authorizedTenantIds.tenantIds())));
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

    // snapshot before activateJobBatch: building the response injects the secret values, which
    // resets the injector and clears the jobs registered for resolution
    final var jobsWithNonCachedSecrets = jobSecretInjector.jobsWithNonCachedSecrets();

    activateJobBatch(record, value, jobBatchKey);

    requestSecretResolution(jobsWithNonCachedSecrets);
  }

  /**
   * Requests the background resolution of the secret references that kept the collector from
   * activating some jobs. Appends one {@code RESOLUTION_REQUESTED} event per reference with the
   * keys of the jobs waiting on it; its applier records the pending reference and parks the jobs so
   * a long poll does not collect them again until the secret is resolved.
   *
   * <p>Stops appending once the record batch is full instead of letting the append overflow and
   * roll back the whole activation. The references left out keep their jobs activatable, so the
   * next activation registers and parks them with a fresh batch budget.
   */
  private void requestSecretResolution(
      final Map<SecretReference, List<Long>> jobsWithNonCachedSecrets) {
    boolean anyRequested = false;
    for (final var waiting : jobsWithNonCachedSecrets.entrySet()) {
      final var reference = waiting.getKey();
      final var event =
          new SecretReferenceRecord()
              .setStoreId(reference.storeId())
              .setSecretReference(reference.name());
      waiting.getValue().forEach(event::addJobKey);
      // the capacity check needs the length of the whole log entry, whose metadata is only
      // decorated with the command's authorization, agent and request source info once the entry is
      // appended. The batch calculation buffer covers that framing on top of the value, the same
      // way the collector sizes the job records it appends; over-estimating only defers a reference
      // to the next activation, which keeps its jobs activatable.
      if (!stateWriter.canWriteEventOfLength(
          event.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
        // stop appending, but still wake for whatever was already appended above
        break;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
      anyRequested = true;
    }
    if (anyRequested) {
      // once per activation rather than per reference: the flag it sets is consumed by whichever
      // cycle runs next, so setting it more than once per activation adds nothing
      secretResolutionScheduler.wake();
    }
  }

  private void rejectCommand(final TypedRecord<JobBatchRecord> record, final Rejection rejection) {
    rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(record, rejection.type(), rejection.reason());
  }

  private void activateJobBatch(
      final TypedRecord<JobBatchRecord> record,
      final JobBatchRecord value,
      final long jobBatchKey) {
    // building the response can drop jobs from the batch (those whose injected secret values
    // would exceed the max message size), so it must happen before the ACTIVATED event
    final var response = responseValueFor(record, value);
    // append (and apply to state) the ACTIVATED event with the unresolved placeholders
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        jobBatchKey, JobBatchIntent.ACTIVATED, response, record);
    countActivatedJobs(value);
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
      final TypedRecord<JobBatchRecord> record, final JobBatchRecord value) {
    if (!record.hasRequestMetadata() || !jobSecretInjector.hasSecretsToInject()) {
      return value;
    }
    responseValue.copyFrom(value);
    jobSecretInjector
        .injectSecretValues(
            responseValue, value, record.getLength(), stateWriter::canWriteEventOfLength)
        .ifPresent(this::raiseIncidentForDroppedJob);
    return responseValue;
  }

  /** Raises the incident matching the reason the injection dropped the job from the activation. */
  private void raiseIncidentForDroppedJob(final DroppedJob droppedJob) {
    switch (droppedJob) {
      case final OversizedJob oversized ->
          raiseIncidentJobSecretValuesTooLargeForMessageSize(oversized);
      case final FailedInjectionJob failed -> raiseIncidentJobSecretInjectionFailed(failed);
    }
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

  /**
   * Raises an incident for a job whose secret value injection failed, with the message {@link
   * JobSecretInjectionIncident} gives both activation paths. The job is also excluded from
   * activation until this incident is resolved (see IncidentCreatedV2Applier's
   * SECRET_RESOLUTION_ERROR handling), so it does not loop through repeated failing injections.
   */
  private void raiseIncidentJobSecretInjectionFailed(final FailedInjectionJob failed) {
    raiseJobIncident(
        failed.jobKey(),
        failed.job(),
        ErrorType.SECRET_RESOLUTION_ERROR,
        JobSecretInjectionIncident.messageFor(failed));
  }

  private void raiseMessageSizeExceededIncident(
      final long jobKey, final JobRecord job, final String message) {
    raiseJobIncident(jobKey, job, ErrorType.MESSAGE_SIZE_EXCEEDED, message);
  }

  private void raiseJobIncident(
      final long jobKey, final JobRecord job, final ErrorType errorType, final String message) {
    incidentBehavior.createJobIncident(jobKey, job, errorType, message);
  }
}
