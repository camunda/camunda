/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.loggers.JobAuthorizationLogger;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobIncidentBehavior;
import io.camunda.zeebe.engine.processing.job.JobSecretValues;
import io.camunda.zeebe.engine.processing.job.JobSecretValues.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretValues.SecretCheckResult;
import io.camunda.zeebe.engine.processing.job.JobVariablesCollector;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger LOGGER = LoggerFactory.getLogger(BpmnJobActivationBehavior.class);

  private static final String SECRET_INJECTION_FAILED_MESSAGE =
      "The job with key '%s' can not be pushed to a job worker, because injecting its secret values "
          + "into the job variables failed. The error details are only logged, to keep possible "
          + "secret data out of persisted records.";

  private final JobStreamer jobStreamer;
  private final JobVariablesCollector jobVariablesCollector;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final KeyGenerator keyGenerator;
  private final JobProcessingMetrics jobMetrics;
  private final InstantSource clock;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final JobAuthorizationLogger jobAuthorizationLogger;
  private final JobSecretValues secretValues;
  private final JobIncidentBehavior jobIncidentBehavior;

  public BpmnJobActivationBehavior(
      final JobStreamer jobStreamer,
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final InstantSource clock,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final SecretStoreRegistry secretStoreRegistry,
      final IncidentMetrics incidentMetrics) {
    this.jobStreamer = jobStreamer;
    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    jobVariablesCollector = new JobVariablesCollector(state);
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    this.clock = clock;
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    this.jobAuthorizationLogger = JobAuthorizationLogger.createDefault();
    secretValues = new JobSecretValues(secretStoreRegistry);
    jobIncidentBehavior =
        new JobIncidentBehavior(state, keyGenerator, writers.state(), incidentMetrics);
  }

  /**
   * Hands the job to a job worker: pushes it on a matching job stream, or notifies the workers that
   * a job of its type is available when no stream matches.
   *
   * <p>A job whose input mappings reference secrets is only pushed once every reference has a
   * cached value; the values are injected into the pushed job, never into the activation event. A
   * job with a non-cached reference is not activated at all: its resolution is requested and the
   * job is parked until it resolves, which pushes it (see {@code
   * SecretReferenceBatchReactivateJobsProcessor}).
   */
  public void publishWork(final long jobKey, final JobRecord jobRecord) {
    publishWork(jobKey, jobRecord, new HashSet<>());
  }

  /**
   * Hands the job to a job worker like {@link #publishWork(long, JobRecord)}, but notifies the
   * workers of a job type only once per {@code notifiedJobTypes}. A caller that hands out several
   * jobs that became available together (e.g. the jobs a resolved secret reference reactivates)
   * passes one set for all of them, so the same notification is not broadcast once per job.
   *
   * <p>The job record is read before this returns, so a caller may pass the record instance the job
   * state reuses across reads.
   */
  public void publishWork(
      final long jobKey, final JobRecord jobRecord, final Set<String> notifiedJobTypes) {
    final JobRecord wrappedJobRecord = new JobRecord();
    wrappedJobRecord.wrapWithoutVariables(jobRecord);

    final String jobType = wrappedJobRecord.getType();
    final JobKind jobKind = wrappedJobRecord.getJobKind();
    final Optional<JobStream> optionalJobStream =
        jobStreamer.streamFor(
            wrappedJobRecord.getTypeBuffer(),
            jobActivationProperties -> isAuthorized(jobActivationProperties, wrappedJobRecord));

    if (optionalJobStream.isEmpty()) {
      // the job stays activatable; a long poll checks its secret references when it collects it
      notifyJobAvailableOnce(jobType, jobKind, notifiedJobTypes);
      return;
    }

    // the values are materialized here and injected below, so a value evicted from the cache in
    // between cannot leave the job with a placeholder it was activated for
    secretValues.reset();
    final SecretCheckResult secrets = secretValues.check(wrappedJobRecord);
    if (!secrets.nonCachedSecrets().isEmpty()) {
      requestResolutionAndPark(jobKey, jobType, jobKind, secrets, notifiedJobTypes);
      return;
    }

    final JobStream jobStream = optionalJobStream.get();
    final JobActivationProperties properties = jobStream.properties();

    setJobProperties(wrappedJobRecord, properties);
    jobVariablesCollector.setJobVariables(properties.fetchVariables(), wrappedJobRecord);
    final var pushableJobRecord = new JobRecord();
    cloneJob(wrappedJobRecord, pushableJobRecord);
    if (!injectSecretValues(jobKey, pushableJobRecord, secrets)) {
      // the job is not activated, so it stays available and its incident tells why it is not pushed
      return;
    }

    // activate job in state; the batch drops the variables, so the injected values stay off the log
    final JobBatchRecord jobBatchRecord = createJobBatchRecord(wrappedJobRecord, properties);
    appendJobToBatch(jobBatchRecord, jobKey, wrappedJobRecord);
    final var jobBatchKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, jobBatchRecord);

    final var activatedJob = new ActivatedJobImpl();
    activatedJob.setJobKey(jobKey).setRecord(pushableJobRecord);

    // job push through side effect
    sideEffectWriter.appendSideEffect(
        () -> {
          jobStream.push(activatedJob);
          jobMetrics.countJobEvent(JobAction.PUSHED, jobKind, jobType);
          return true;
        });
  }

  /**
   * Requests the background resolution of the job's non-cached references, one {@code
   * RESOLUTION_REQUESTED} event per reference, whose applier parks the job until they are resolved.
   *
   * <p>Stops appending once the record batch is full instead of letting the append overflow and
   * roll back the command being processed. A job already parked by an earlier reference of this
   * call is left parked: its remaining references are requested when the reactivation pushes it
   * again. A job that could not be parked at all stays activatable, so a long poll can still
   * collect it and request the resolution itself.
   */
  private void requestResolutionAndPark(
      final long jobKey,
      final String jobType,
      final JobKind jobKind,
      final SecretCheckResult secrets,
      final Set<String> notifiedJobTypes) {
    final Set<SecretReference> requested = new HashSet<>();
    boolean parked = false;
    for (final Secret secret : secrets.nonCachedSecrets()) {
      if (!requested.add(secret.reference())) {
        continue;
      }
      final var event =
          new SecretReferenceRecord()
              .setStoreId(secret.reference().storeId())
              .setSecretReference(secret.reference().name())
              .addJobKey(jobKey);
      // the capacity check needs the length of the whole log entry, whose metadata is only
      // decorated once the entry is appended. The batch calculation buffer covers that framing on
      // top of the value, the same way the collector sizes the job records it appends
      if (!stateWriter.canWriteEventOfLength(
          event.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
        break;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
      parked = true;
    }
    if (!parked) {
      notifyJobAvailableOnce(jobType, jobKind, notifiedJobTypes);
    }
    jobMetrics.countJobEvent(JobAction.SKIPPED, jobKind, jobType);
  }

  /** Notifies the workers of the job type unless this batch of jobs already did. */
  private void notifyJobAvailableOnce(
      final String jobType, final JobKind jobKind, final Set<String> notifiedJobTypes) {
    if (notifiedJobTypes.add(jobType)) {
      notifyJobAvailable(jobType, jobKind);
    }
  }

  /**
   * Replaces the secret placeholders in the variables of the job to push with the values found for
   * them, and returns whether the job can be pushed. A failed injection raises an incident and
   * keeps the job from being pushed, so it cannot reach a worker with a placeholder where a value
   * belongs. The failure details are only logged, so no secret-related data can end up in persisted
   * records.
   */
  private boolean injectSecretValues(
      final long jobKey, final JobRecord pushableJobRecord, final SecretCheckResult secrets) {
    if (secrets.cachedSecrets().isEmpty()) {
      return true;
    }
    try {
      final byte[] injected =
          secretValues.injectedVariablesOf(pushableJobRecord, secrets.cachedSecrets());
      if (injected != null) {
        pushableJobRecord.setVariables(BufferUtil.wrapArray(injected));
      }
      return true;
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to inject secret values into the variables of the job with key {} of type '{}'; "
              + "the job is not pushed and gets an incident",
          jobKey,
          pushableJobRecord.getType(),
          e);
      jobIncidentBehavior.createIncident(
          jobKey,
          pushableJobRecord,
          ErrorType.SECRET_RESOLUTION_ERROR,
          SECRET_INJECTION_FAILED_MESSAGE.formatted(jobKey));
      return false;
    }
  }

  public void notifyJobAvailableAsSideEffect(final JobRecord jobRecord) {
    notifyJobAvailable(jobRecord.getType(), jobRecord.getJobKind());
  }

  private void notifyJobAvailable(final String jobType, final JobKind jobKind) {
    sideEffectWriter.appendSideEffect(
        () -> {
          jobStreamer.notifyWorkAvailable(jobType);
          jobMetrics.countJobEvent(JobAction.WORKERS_NOTIFIED, jobKind, jobType);
          return true;
        });
  }

  private void setJobProperties(
      final JobRecord jobRecord, final JobActivationProperties properties) {
    // we push the job immediately, so the deadline is always calculated from the current time
    final var deadline = clock.millis() + properties.timeout();
    jobRecord.setDeadline(deadline);
    jobRecord.setWorker(properties.worker());
  }

  private JobBatchRecord createJobBatchRecord(
      final JobRecord jobRecord, final JobActivationProperties properties) {
    // reuse the existing JobBatch activation mechanism
    final JobBatchRecord jobBatchRecord = new JobBatchRecord();
    jobBatchRecord
        .setType(jobRecord.getType())
        .setTimeout(properties.timeout())
        .setWorker(properties.worker());
    return jobBatchRecord;
  }

  private void appendJobToBatch(
      final JobBatchRecord jobBatchRecord, final Long jobKey, final JobRecord jobRecord) {

    // we don't need to clone the job record, as the buffer isn't reused
    jobBatchRecord.jobKeys().add().setValue(jobKey);
    jobBatchRecord.jobs().add().wrapWithoutVariables(jobRecord);
  }

  private void cloneJob(final JobRecord jobRecord, final JobRecord jobRecordClone) {
    final var bytes = new byte[jobRecord.getLength()];
    final var jobCopyBuffer = new UnsafeBuffer(bytes);
    jobRecord.write(jobCopyBuffer, 0);
    jobRecordClone.wrap(jobCopyBuffer, 0, jobRecord.getLength());
  }

  private boolean isAuthorized(
      final JobActivationProperties jobActivationProperties, final JobRecord jobRecord) {

    final var ownerTenantId = jobRecord.getTenantId();
    final var isTenantAuthorized =
        switch (jobActivationProperties.tenantFilter()) {
          case ASSIGNED -> {
            final var authorizedTenants =
                tenantCheck.resolveAuthorizedTenants(jobActivationProperties.claims());
            yield !authorizedTenants.wildcard()
                && authorizedTenants.isAuthorizedForTenantId(ownerTenantId);
          }
          case PROVIDED -> jobActivationProperties.tenantIds().contains(ownerTenantId);
        };
    if (!isTenantAuthorized) {
      // don't push jobs to workers that don't request them from the job's tenant
      jobAuthorizationLogger.logUnauthorizedTenantAccess(jobActivationProperties, jobRecord);
      return false;
    }

    final var claims = jobActivationProperties.claims();
    final var bpmnProcessId = jobRecord.getBpmnProcessId();
    final var cslPermType = AuthzModelMapper.fromProtocol(PermissionType.UPDATE_PROCESS_INSTANCE);
    final var cslResourceType =
        AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROCESS_DEFINITION);
    final var authorizationResult =
        cslCheck.checkWithClaims(
            claims,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(cslResourceType)
                        .permissionType(cslPermType)
                        .resourceId(bpmnProcessId)),
            bpmnProcessId,
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION));

    authorizationResult.ifLeft(
        ignored ->
            jobAuthorizationLogger.logUnauthorizedResourceAccess(
                jobActivationProperties, jobRecord));

    return authorizationResult.isRight();
  }

  private boolean isAuthorizedForJob(
      final JobRecord jobRecord, final Set<AuthorizationScope> authorizedProcessIds) {
    return authorizedProcessIds.contains(AuthorizationScope.WILDCARD)
        || authorizedProcessIds.contains(AuthorizationScope.id(jobRecord.getBpmnProcessId()));
  }
}
