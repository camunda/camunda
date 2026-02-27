/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.job.JobVariablesCollector;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.Optional;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;

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
  private final JobProcessingMetrics jobMetrics;
  private final InstantSource clock;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public BpmnJobActivationBehavior(
      final JobStreamer jobStreamer,
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.jobStreamer = jobStreamer;
    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    jobVariablesCollector = new JobVariablesCollector(state);
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    this.clock = clock;
    authorizationCheckBehavior = authCheckBehavior;
  }

  public void publishWork(final long jobKey, final JobRecord jobRecord) {
    final JobRecord wrappedJobRecord = new JobRecord();
    wrappedJobRecord.wrapWithoutVariables(jobRecord);

    final String jobType = wrappedJobRecord.getType();
    final JobKind jobKind = wrappedJobRecord.getJobKind();
    final Optional<JobStream> optionalJobStream =
        jobStreamer.streamFor(
            wrappedJobRecord.getTypeBuffer(),
            jobActivationProperties -> isAuthorized(jobActivationProperties, wrappedJobRecord));

    if (optionalJobStream.isPresent()) {
      final JobStream jobStream = optionalJobStream.get();
      final JobActivationProperties properties = jobStream.properties();

      // activate job in state
      setJobProperties(wrappedJobRecord, properties);
      final JobBatchRecord jobBatchRecord = createJobBatchRecord(wrappedJobRecord, properties);
      appendJobToBatch(jobBatchRecord, jobKey, wrappedJobRecord);
      final var jobBatchKey = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, jobBatchRecord);

      jobVariablesCollector.setJobVariables(properties.fetchVariables(), wrappedJobRecord);
      final var pushableJobRecord = new JobRecord();
      cloneJob(wrappedJobRecord, pushableJobRecord);
      final var activatedJob = new ActivatedJobImpl();
      activatedJob.setJobKey(jobKey).setRecord(pushableJobRecord);

      // job push through side effect
      sideEffectWriter.appendSideEffect(
          () -> {
            jobStream.push(activatedJob);
            jobMetrics.countJobEvent(JobAction.PUSHED, jobKind, jobType);
            return true;
          });
    } else {
      notifyJobAvailable(jobType, jobKind);
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
                authorizationCheckBehavior.getAuthorizedTenantIds(jobActivationProperties.claims());
            yield !authorizedTenants.isAnonymous()
                && authorizedTenants.isAuthorizedForTenantId(ownerTenantId);
          }
          case PROVIDED -> jobActivationProperties.tenantIds().contains(ownerTenantId);
        };
    if (!isTenantAuthorized) {
      // don't push jobs to workers that don't request them from the job's tenant
      return false;
    }

    final var claims = jobActivationProperties.claims();
    return authorizationCheckBehavior
        .isAuthorized(
            AuthorizationRequest.builder()
                .authorizationClaims(claims)
                .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
                .addResourceId(jobRecord.getBpmnProcessId())
                .tenantId(ownerTenantId)
                .build())
        .isRight(); // we only care if the job stream is authorized, not why it isn't
  }

  private boolean isAuthorizedForJob(
      final JobRecord jobRecord, final Set<AuthorizationScope> authorizedProcessIds) {
    return authorizedProcessIds.contains(AuthorizationScope.WILDCARD)
        || authorizedProcessIds.contains(AuthorizationScope.id(jobRecord.getBpmnProcessId()));
  }
}
