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
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.job.JobBatchCollector.TooLargeJob;
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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

@ExcludeAuthorizationCheck
public final class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final JobBatchCollector jobBatchCollector;
  private final KeyGenerator keyGenerator;
  private final JobProcessingMetrics jobMetrics;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public JobBatchActivateProcessor(
      final Writers writers,
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final AuthorizationCheckBehavior authCheckBehavior,
      final InstantSource clock) {

    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    authorizationCheckBehavior = authCheckBehavior;
    jobBatchCollector =
        new JobBatchCollector(state, stateWriter::canWriteEventOfLength, authCheckBehavior, clock);

    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(record);
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

    final Either<TooLargeJob, Map<JobKind, Integer>> result =
        jobBatchCollector.collectJobs(record, tenantIds);
    final var activatedJobCountPerJobKind = result.getOrElse(Collections.emptyMap());
    result.ifLeft(
        largeJob ->
            raiseIncidentJobTooLargeForMessageSize(
                largeJob.key(), largeJob.jobRecord(), largeJob.expectedEventLength()));

    activateJobBatch(record, value, jobBatchKey, activatedJobCountPerJobKind);
  }

  private void rejectCommand(final TypedRecord<JobBatchRecord> record, final Rejection rejection) {
    rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
    responseWriter.writeRejectionOnCommand(record, rejection.type(), rejection.reason());
  }

  private void activateJobBatch(
      final TypedRecord<JobBatchRecord> record,
      final JobBatchRecord value,
      final long jobBatchKey,
      final Map<JobKind, Integer> activatedJobsCountPerJobKind) {
    stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeEventOnCommand(jobBatchKey, JobBatchIntent.ACTIVATED, value, record);
    activatedJobsCountPerJobKind.forEach(
        (jobKind, count) ->
            jobMetrics.countJobEvent(JobAction.ACTIVATED, jobKind, value.getType(), count));
  }

  private void raiseIncidentJobTooLargeForMessageSize(
      final long jobKey, final JobRecord job, final int expectedJobRecordSize) {
    final String jobSize = ByteValue.prettyPrint(expectedJobRecordSize);
    final DirectBuffer incidentMessage =
        wrapString(
            String.format(
                "The job with key '%s' can not be activated, because with %s it is larger than the configured message size (per default is 4 MB). "
                    + "Try to reduce the size by reducing the number of fetched variables or modifying the variable values.",
                jobKey, jobSize));

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
  }
}
