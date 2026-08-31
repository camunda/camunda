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
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretInjectionIncident;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.job.JobVariablesCollector;
import io.camunda.zeebe.engine.processing.job.LeaseTokens;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
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
import java.util.function.Predicate;
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
  private final JobSecretLookup secretLookup;
  private final SecretReferenceState secretReferenceState;
  private final BpmnIncidentBehavior incidentBehavior;

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
      final BpmnIncidentBehavior incidentBehavior) {
    this.jobStreamer = jobStreamer;
    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    jobVariablesCollector = new JobVariablesCollector(state);
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    this.clock = clock;
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    jobAuthorizationLogger = JobAuthorizationLogger.createDefault();
    secretLookup = new JobSecretLookup(secretStoreRegistry);
    secretReferenceState = state.getSecretReferenceState();
    this.incidentBehavior = incidentBehavior;
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
   *
   * <p>A job that stays activatable because no stream matched its type has its non-cached
   * references warmed instead: the resolution is requested without parking the job, so a long
   * poll's own check (see {@code JobBatchCollector}) is more likely to find the value already
   * cached by the time it runs. That poll still parks the job itself on a miss; this only gives the
   * resolution a head start over it.
   */
  public void publishWork(final long jobKey, final JobRecord jobRecord) {
    publishWork(jobKey, jobRecord, new HashSet<>());
  }

  /**
   * Estimate of what handing this job out adds to the record batch, for a caller that hands out
   * several jobs into one batch and wants to cut the batch before it runs out of room (see {@code
   * SecretReferenceBatchReactivateJobsProcessor}).
   *
   * <p>An estimate, not a bound: it is measured on the stored job record, which does not carry the
   * worker name of the stream that ends up taking the job, and a hand-out writes that name twice,
   * on the job it puts in the activation batch and on the batch itself. A caller therefore cannot
   * size a hand-out it has not made yet. What keeps a hand-out inside the batch is the hand-out
   * itself: it checks before it appends and reports back when the batch is out of room.
   */
  public static int maxHandOutLength(final JobRecord jobRecord) {
    return jobRecord.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
  }

  /**
   * Hands the job to a job worker like {@link #publishWork(long, JobRecord)}, but notifies the
   * workers of a job type only once per {@code notifiedJobTypes}. A caller that hands out several
   * jobs that became available together (e.g. the jobs a resolved secret reference reactivates)
   * passes one set for all of them, so the same notification is not broadcast once per job.
   *
   * <p>The job record is read before this returns, so a caller may pass the record instance the job
   * state reuses across reads.
   *
   * @return whether the record batch had room for this hand-out. A caller handing out several jobs
   *     into one batch must stop when this is {@code false}: the batch is out of room, so the jobs
   *     after this one would only be turned away too, and the append that does not fit would fail
   *     the whole command rather than this one job.
   */
  public boolean publishWork(
      final long jobKey, final JobRecord jobRecord, final Set<String> notifiedJobTypes) {
    final JobRecord wrappedJobRecord = new JobRecord();
    wrappedJobRecord.wrapWithoutVariables(jobRecord);

    final String jobType = wrappedJobRecord.getType();
    final JobKind jobKind = wrappedJobRecord.getJobKind();
    final var leaseAwarePredicate = new LeaseAwarePredicate(wrappedJobRecord);
    final Optional<JobStream> optionalJobStream =
        jobStreamer.streamFor(
            wrappedJobRecord.getTypeBuffer(),
            jobActivationProperties ->
                isAuthorized(jobActivationProperties, wrappedJobRecord)
                    && leaseAwarePredicate.test(jobActivationProperties));

    if (optionalJobStream.isEmpty()) {
      if (leaseAwarePredicate.isLeasedJobSkipped()) {
        // push-side counterpart of the skip JobBatchCollector counts when a poll request without
        // withLease encounters an already-leased job: here, a stream matched the type but not the
        // lease, so the job is demoted to waiting for a poller instead
        sideEffectWriter.appendSideEffect(
            () -> {
              jobMetrics.countJobEvent(JobAction.SKIPPED_LEASED, jobKind, jobType);
            });
      }
      // the job stays activatable; a long poll checks its secret references when it collects it.
      // Warming the resolution here only gives that check a head start, it never replaces it
      requestWarmResolution(jobKey, wrappedJobRecord);
      notifyJobAvailableOnce(jobType, jobKind, notifiedJobTypes);
      return true;
    }

    // the check reads the values that are injected below, so a value evicted from the cache in
    // between cannot leave the job with a placeholder it was activated for. They live on the check
    // result only, which ends with this hand-out: afterwards the only plaintext value left is the
    // one in the pushed job itself, which carries it to the worker
    final SecretCheckResult secrets = secretLookup.check(wrappedJobRecord);
    if (!secrets.nonCachedSecrets().isEmpty()) {
      return requestResolutionAndPark(jobKey, jobType, jobKind, secrets, notifiedJobTypes);
    }

    final JobStream jobStream = optionalJobStream.get();
    final JobActivationProperties properties = jobStream.properties();

    setJobProperties(wrappedJobRecord, properties);
    jobVariablesCollector.setJobVariables(properties.fetchVariables(), wrappedJobRecord);
    final var pushableJobRecord = new JobRecord();
    cloneJob(wrappedJobRecord, pushableJobRecord);
    if (!injectSecretValues(jobKey, pushableJobRecord, secrets)) {
      // the job is not activated, and the incident took it out of the activation until an operator
      // resolves it. The batch itself is fine, so a caller handing out more jobs should carry on
      return true;
    }

    // activate job in state; the batch drops the variables, so the injected values stay off the log
    final JobBatchRecord jobBatchRecord = createJobBatchRecord(wrappedJobRecord, properties);
    appendJobToBatch(jobBatchRecord, jobKey, wrappedJobRecord);
    // the activation is sized here rather than by the caller: only now is the stream known, and
    // with it the worker name this record carries twice. Letting the append overflow instead would
    // fail the whole command, which for an engine-written one means no rejection anybody reads
    if (!stateWriter.canWriteEventOfLength(
        jobBatchRecord.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
      // the job is not activated, so it stays available; the notification lets a long poll collect
      // it, and the caller stops handing out jobs this batch can no longer take
      notifyJobAvailableOnce(jobType, jobKind, notifiedJobTypes);
      return false;
    }
    final var jobBatchKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, jobBatchRecord);

    final var activatedJob = new ActivatedJobImpl();
    activatedJob.setJobKey(jobKey).setRecord(pushableJobRecord);

    // job push through side effect
    sideEffectWriter.appendSideEffect(
        () -> {
          jobStream.push(activatedJob);
          jobMetrics.countJobEvent(JobAction.PUSHED, jobKind, jobType);
        });
    return true;
  }

  /**
   * Requests the background resolution of the job's non-cached references, one {@code
   * RESOLUTION_REQUESTED} event per reference, whose applier parks the job until they are resolved.
   *
   * <p>Stops appending once the record batch is full instead of letting the append overflow and
   * roll back the command being processed. A job already parked by an earlier reference of this
   * call is left parked: its remaining references are requested when the reactivation pushes it
   * again. A job that could not be parked at all stays activatable and is announced to the workers
   * instead of being counted as skipped, so a long poll can still collect it and request the
   * resolution itself.
   *
   * @return whether the record batch had room for every request, i.e. {@code false} once it ran out
   */
  private boolean requestResolutionAndPark(
      final long jobKey,
      final String jobType,
      final JobKind jobKind,
      final SecretCheckResult secrets,
      final Set<String> notifiedJobTypes) {
    final Set<SecretReference> requested = new HashSet<>();
    boolean parked = false;
    boolean batchHadRoom = true;
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
        batchHadRoom = false;
        break;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
      parked = true;
    }
    if (parked) {
      jobMetrics.countJobEvent(JobAction.SKIPPED_UNCACHED_SECRET, jobKind, jobType);
    } else {
      notifyJobAvailableOnce(jobType, jobKind, notifiedJobTypes);
    }
    return batchHadRoom;
  }

  /**
   * Requests the background resolution of the job's non-cached references without parking the job
   * or its jobs keys: a long poll still runs {@link JobSecretLookup#check} itself when it collects
   * the job (see {@code JobBatchCollector}) and parks it on a miss exactly as it does today, so
   * this only gives the resolution a head start over that first poll. A reference already pending
   * is not requested again, so many jobs referencing the same non-cached secret in one window cost
   * one request, not one per job.
   *
   * <p>Stops appending once the record batch is full instead of letting the append overflow and
   * roll back the command being processed; the references left out are requested again by whichever
   * poll or push eventually reaches the job, exactly as if this warmup had not run.
   *
   * <p>A failing check is caught rather than left to propagate: unlike the poll's own check (see
   * {@code JobBatchCollector}) or the push's real check above, whose failure is the caller's only
   * way to learn a secret store is broken, this runs piggy-backed on whatever command happened to
   * create or reactivate the job - a warmup failure must not fail that unrelated command. The next
   * poll or push still runs the real check and surfaces the same failure through its own, correct
   * path.
   */
  private void requestWarmResolution(final long jobKey, final JobRecord jobRecord) {
    final SecretCheckResult secrets;
    try {
      secrets = secretLookup.check(jobRecord);
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to warm the secret resolution of the job with key {} of type '{}'; a later poll "
              + "or push will check its references again",
          jobKey,
          jobRecord.getType(),
          e);
      return;
    }
    if (secrets.nonCachedSecrets().isEmpty()) {
      return;
    }
    final Set<SecretReference> requested = new HashSet<>();
    for (final Secret secret : secrets.nonCachedSecrets()) {
      final SecretReference reference = secret.reference();
      if (!requested.add(reference)
          || secretReferenceState.isPending(reference.storeId(), reference.name())) {
        continue;
      }
      final var event =
          new SecretReferenceRecord()
              .setStoreId(reference.storeId())
              .setSecretReference(reference.name());
      if (!stateWriter.canWriteEventOfLength(
          event.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
        return;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
    }
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
   * belongs. Raising that incident also takes the job out of the activation until an operator
   * resolves it, so a failing injection cannot be retried on every activation (see {@code
   * IncidentCreatedV2Applier}). The failure details are only logged, since the exception may quote
   * the variables document; the incident itself carries the message that {@link
   * JobSecretInjectionIncident} gives both activation paths.
   */
  private boolean injectSecretValues(
      final long jobKey, final JobRecord pushableJobRecord, final SecretCheckResult secrets) {
    if (secrets.cachedSecrets().isEmpty()) {
      return true;
    }
    try {
      final byte[] injected =
          secretLookup.injectedVariablesOf(pushableJobRecord, secrets.cachedSecrets());
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
      incidentBehavior.createJobIncident(
          jobKey,
          pushableJobRecord,
          ErrorType.SECRET_RESOLUTION_ERROR,
          JobSecretInjectionIncident.messageFor(jobKey, e));
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
        });
  }

  private void setJobProperties(
      final JobRecord jobRecord, final JobActivationProperties properties) {
    // we push the job immediately, so the deadline is always calculated from the current time
    final var deadline = clock.millis() + properties.timeout();
    jobRecord.setDeadline(deadline);
    jobRecord.setWorker(properties.worker());
    if (properties.withLease()) {
      jobRecord.setLeaseToken(LeaseTokens.generate());
    }
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

  /**
   * Matches streams that are lease-compatible for the given job, tracking whether an already-leased
   * job was skipped because an otherwise-authorized candidate does not request leases. That
   * distinction is what {@link #publishWork} needs to decide whether falling back to a notification
   * is a plain "no stream registered" case or a skip due to lease incompatibility. Authorization is
   * a separate concern with no state to track, so it stays a plain method composed alongside this
   * predicate at the call site rather than folded into it.
   */
  private static final class LeaseAwarePredicate implements Predicate<JobActivationProperties> {
    private final JobRecord jobRecord;
    private boolean leasedJobSkipped;

    private LeaseAwarePredicate(final JobRecord jobRecord) {
      this.jobRecord = jobRecord;
    }

    @Override
    public boolean test(final JobActivationProperties jobActivationProperties) {
      if (jobActivationProperties.withLease() || !jobRecord.hasLeaseToken()) {
        return true;
      }
      leasedJobSkipped = true;
      return false;
    }

    private boolean isLeasedJobSkipped() {
      return leasedJobSkipped;
    }
  }
}
