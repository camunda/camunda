/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.common.BannedInstanceCommandCheck;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IncidentResolveProcessor
    implements TypedRecordProcessor<IncidentRecord>, SuspensionAware<IncidentRecord> {

  public static final String NO_RETRIES_LEFT_MSG =
      "Expected to resolve incident with key '%d', but job with key '%d' has no retries left. Please update the job retries and retry resolving the incident";
  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";
  private static final String ELEMENT_NOT_IN_SUPPORTED_STATE_MSG =
      "Expected incident to refer to element in state ELEMENT_ACTIVATING, ELEMENT_COMPLETING, or ELEMENT_TERMINATING, but element is in state %s";
  private static final String UNEXPECTED_LIFECYCLE_STATE_CONVERSION_MSG =
      "Unexpected user task lifecycle state: '%s' encountered during conversion to failed user task command.";

  private final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor;
  private final TypedRecordProcessor<UserTaskRecord> userTaskProcessor;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final IncidentState incidentState;
  private final ElementInstanceState elementInstanceState;
  private final UserTaskState userTaskState;
  private final TypedResponseWriter responseWriter;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final JobState jobState;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final IncidentMetrics incidentMetrics;
  private final BannedInstanceCommandCheck bannedInstanceCheck;
  private final KeyGenerator keyGenerator;
  private final SecretResolutionScheduler secretResolutionScheduler;
  private final JobSecretLookup secretLookup;

  public IncidentResolveProcessor(
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final TypedRecordProcessor<UserTaskRecord> userTaskProcessor,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final IncidentMetrics incidentMetrics,
      final KeyGenerator keyGenerator,
      final SecretResolutionScheduler secretResolutionScheduler,
      final SecretStoreRegistry secretStoreRegistry) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    this.userTaskProcessor = userTaskProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    incidentState = processingState.getIncidentState();
    elementInstanceState = processingState.getElementInstanceState();
    userTaskState = processingState.getUserTaskState();
    this.jobActivationBehavior = jobActivationBehavior;
    jobState = processingState.getJobState();
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    this.incidentMetrics = incidentMetrics;
    this.secretResolutionScheduler = secretResolutionScheduler;
    this.keyGenerator = keyGenerator;
    secretLookup = new JobSecretLookup(secretStoreRegistry);
    bannedInstanceCheck = new BannedInstanceCommandCheck(processingState.getBannedInstanceState());
  }

  @Override
  public void processRecord(final TypedRecord<IncidentRecord> command) {
    final long key = command.getKey();
    final var authorizedTenantIds =
        tenantCheck.resolveAuthorizedTenants(command.getAuthorizations());
    final var incident = incidentState.getIncidentRecord(key, authorizedTenantIds);
    if (incident == null) {
      final var errorMessage = String.format(NO_INCIDENT_FOUND_MSG, key);
      rejectResolveCommand(command, errorMessage, RejectionType.NOT_FOUND);
      return;
    }

    // Check if the process instance is banned
    final var bannedInstanceCheckResult = bannedInstanceCheck.check(incident);
    if (bannedInstanceCheckResult.isLeft()) {
      final var rejection = bannedInstanceCheckResult.getLeft();
      enrichRejectionCommand(command, incident);
      rejectResolveCommand(command, rejection.reason(), rejection.type());
      return;
    }

    final var isAuthorized =
        cslCheck.check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.UPDATE_PROCESS_INSTANCE))
                        .resourceId(incident.getBpmnProcessId())),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      enrichRejectionCommand(command, incident);
      rejectResolveCommand(command, rejection.reason(), rejection.type());
      return;
    }

    final long jobKey = incident.getJobKey();
    if (isJobRelatedIncident(jobKey) && jobState.getJob(jobKey).getRetries() <= 0) {
      final var errorMessage = String.format(NO_RETRIES_LEFT_MSG, key, jobKey);
      enrichRejectionCommand(command, incident);
      rejectResolveCommand(command, errorMessage, RejectionType.INVALID_STATE);
      return;
    }

    stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, incident);
    responseWriter.writeAcceptedResponseOnCommand(key, IncidentIntent.RESOLVED, incident, command);
    incidentMetrics.incidentResolved();

    final boolean secretResolutionRequested = requestSecretResolutionAgain(incident, jobKey);
    if (!secretResolutionRequested) {
      publishIncidentRelatedJob(jobKey);
    }

    // if it fails, a new incident is raised
    attemptToContinueProcessProcessing(command, incident);

    // waking the scheduler is not transactional, so it runs only once every step that could throw
    // has succeeded: a rollback would otherwise leave it woken for a resolution the log no longer
    // asks for
    if (secretResolutionRequested) {
      secretResolutionScheduler.stayAwake();
    }
  }

  /**
   * Re-enters the secret resolution lifecycle for a job that was parked on a secret, and reports
   * whether it did. Resolution is otherwise requested only from the two activation paths, so a
   * retry would re-read the store only when a worker happens to be attached to pick the job up —
   * with none, the incident record simply flips to resolved and the instance reads as healthy while
   * the secret is still missing.
   *
   * <p>Only the references the cache cannot answer are requested, which is also what tells the two
   * producers of {@link ErrorType#SECRET_RESOLUTION_ERROR} apart. A missing secret leaves its
   * reference uncached, so it is requested and the incident returns if it is still gone. An
   * injection failure ({@code JobSecretLookup.SecretPointerMismatchException}) resolved its secret
   * fine and failed on where the value had to go, so its references are normally still cached,
   * nothing is requested, and the job goes back to the activation path as before — re-parking it
   * would only strand it on references that are already available. Once the cache has evicted them
   * that retry does request a resolution: the reference resolves, the job is reactivated, and the
   * injection fails again on the next activation. Same outcome, longer route.
   *
   * <p>Every uncached reference is requested, not only the incidented one: a job carrying several
   * missing references gets a single incident, so requesting just that one would leave the others
   * with nothing to ask for them again.
   *
   * <p>A job parked here must not also be published: it cannot run until the resolution answers.
   * Only the requests are appended here — the caller wakes the scheduler, once every step that
   * could throw has succeeded.
   */
  private boolean requestSecretResolutionAgain(final IncidentRecord incident, final long jobKey) {
    if (incident.getErrorType() != ErrorType.SECRET_RESOLUTION_ERROR
        || !isJobRelatedIncident(jobKey)) {
      return false;
    }
    final JobRecord job = jobState.getJob(jobKey);
    if (job == null || !job.hasSecretReferences()) {
      return false;
    }
    final List<SecretReferenceRecord> requests =
        distinctRequestsFor(secretLookup.check(job).nonCachedSecrets(), jobKey);
    if (requests.isEmpty() || !fitsInRecordBatch(requests)) {
      // all of them or none: the first request parks the job, and with no activation to come back
      // for the rest, a partially requested job would wait on references nobody asks for again
      return false;
    }
    requests.forEach(
        request ->
            stateWriter.appendFollowUpEvent(
                keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, request));
    return true;
  }

  /**
   * One request per distinct store and reference, so a name used at two paths is asked for once.
   */
  private static List<SecretReferenceRecord> distinctRequestsFor(
      final List<Secret> secrets, final long jobKey) {
    final Map<SecretReference, SecretReferenceRecord> requests = new LinkedHashMap<>();
    for (final Secret secret : secrets) {
      requests.computeIfAbsent(
          secret.reference(),
          reference ->
              new SecretReferenceRecord()
                  .setStoreId(reference.storeId())
                  .setSecretReference(reference.name())
                  .addJobKey(jobKey));
    }
    return List.copyOf(requests.values());
  }

  /**
   * Whether the batch still has room for every request. Measured against their combined length,
   * since they are only ever written together; the calculation buffer covers the log entry framing
   * each one gains on top of its value, the same way the activation paths size theirs.
   */
  private boolean fitsInRecordBatch(final List<SecretReferenceRecord> requests) {
    final int length =
        requests.stream()
            .mapToInt(
                request -> request.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)
            .sum();
    return stateWriter.canWriteEventOfLength(length);
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final String errorMessage,
      final RejectionType rejectionType) {

    rejectionWriter.appendRejection(command, rejectionType, errorMessage);
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, errorMessage);
  }

  /**
   * Enriches the command with fields from the incident record, that are relevant to be exported in
   * the audit log in case of a rejection.
   */
  private void enrichRejectionCommand(
      final TypedRecord<IncidentRecord> command, final IncidentRecord incident) {
    command.getValue().setTenantId(incident.getTenantId());
    command.getValue().setElementInstancePath(incident.getElementInstancePath());
  }

  private void attemptToContinueProcessProcessing(
      final TypedRecord<IncidentRecord> command, final IncidentRecord incident) {

    final long jobKey = incident.getJobKey();
    if (isJobRelatedIncident(jobKey)) {
      return;
    }

    getFailedCommand(incident)
        .ifRightOrLeft(
            this::processFailedCommand,
            failure -> {
              final var message =
                  String.format(
                      "Expected to continue processing after incident %d resolved, but failed command not found",
                      command.getKey());
              throw new IllegalStateException(message, new IllegalStateException(failure));
            });
  }

  private void processFailedCommand(final TypedRecord<? extends UnifiedRecordValue> failedCommand) {
    if (failedCommand.getValue() instanceof ProcessInstanceRecord) {
      bpmnStreamProcessor.processRecord((TypedRecord<ProcessInstanceRecord>) failedCommand);
    } else if (failedCommand.getValue() instanceof UserTaskRecord) {
      userTaskProcessor.processRecord((TypedRecord<UserTaskRecord>) failedCommand);
    } else {
      throw new IllegalStateException(
          "Failed to process command due to unsupported record type: '%s'."
              .formatted(failedCommand.getValue().getClass().getSimpleName()));
    }
  }

  private boolean isUserTaskListenerRelatedIncident(
      final IncidentRecord incidentRecord, final ElementInstance elementInstance) {

    final boolean isExpressionFailure =
        incidentRecord.getErrorType() == ErrorType.EXTRACT_VALUE_ERROR;

    final boolean isCamundaUserTask =
        elementInstance.getValue().getBpmnElementType() == BpmnElementType.USER_TASK
            && elementInstance.getUserTaskKey() > 0;

    final var currentState = elementInstance.getState();
    final boolean isInRelevantState =
        currentState == ProcessInstanceIntent.ELEMENT_ACTIVATED
            || currentState == ProcessInstanceIntent.ELEMENT_TERMINATING;

    return isExpressionFailure && isCamundaUserTask && isInRelevantState;
  }

  private Either<String, TypedRecord<? extends UnifiedRecordValue>> getFailedCommand(
      final IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return Either.left(
          String.format(
              "Expected to find failed command for element instance %d, but element instance not found",
              elementInstanceKey));
    }

    return isUserTaskListenerRelatedIncident(incidentRecord, elementInstance)
        ? createUserTaskCommand(elementInstance)
        : createProcessInstanceCommand(elementInstance);
  }

  private Either<String, TypedRecord<? extends UnifiedRecordValue>> createUserTaskCommand(
      final ElementInstance elementInstance) {

    final var userTaskKey = elementInstance.getUserTaskKey();
    final var intermediateState = userTaskState.getIntermediateState(userTaskKey);

    if (intermediateState == null) {
      return Either.left(
          String.format("No intermediate state found for user task with key %d", userTaskKey));
    }

    return getFailedUserTaskCommandIntent(intermediateState.getLifecycleState())
        .map(
            intent -> {
              final var userTaskRecord = new UserTaskRecord();
              userTaskRecord.wrap(intermediateState.getRecord());
              return new RetryTypedRecord<>(userTaskKey, intent, userTaskRecord);
            });
  }

  private Either<String, TypedRecord<? extends UnifiedRecordValue>> createProcessInstanceCommand(
      final ElementInstance elementInstance) {

    return getFailedProcessInstanceCommandIntent(elementInstance)
        .map(
            intent -> {
              final var record = new ProcessInstanceRecord();
              record.wrap(elementInstance.getValue());
              return new RetryTypedRecord<>(elementInstance.getKey(), intent, record);
            });
  }

  private Either<String, UserTaskIntent> getFailedUserTaskCommandIntent(
      final LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case CREATING -> Either.right(UserTaskIntent.CREATE);
      case ASSIGNING -> Either.right(UserTaskIntent.ASSIGN);
      case CLAIMING -> Either.right(UserTaskIntent.CLAIM);
      case UPDATING -> Either.right(UserTaskIntent.UPDATE);
      case COMPLETING -> Either.right(UserTaskIntent.COMPLETE);
      case CANCELING -> Either.right(UserTaskIntent.CANCEL);
      default ->
          Either.left(String.format(UNEXPECTED_LIFECYCLE_STATE_CONVERSION_MSG, lifecycleState));
    };
  }

  private Either<String, ProcessInstanceIntent> getFailedProcessInstanceCommandIntent(
      final ElementInstance elementInstance) {
    final var instanceState = elementInstance.getState();
    return switch (instanceState) {
      case ELEMENT_ACTIVATING -> Either.right(ProcessInstanceIntent.ACTIVATE_ELEMENT);
      case ELEMENT_COMPLETING -> Either.right(ProcessInstanceIntent.COMPLETE_ELEMENT);
      case ELEMENT_TERMINATING -> Either.right(ProcessInstanceIntent.TERMINATE_ELEMENT);
      default -> Either.left(String.format(ELEMENT_NOT_IN_SUPPORTED_STATE_MSG, instanceState));
    };
  }

  private void publishIncidentRelatedJob(final long jobKey) {
    if (isJobRelatedIncident(jobKey)) {
      final JobRecord failedJobRecord = jobState.getJob(jobKey);
      jobActivationBehavior.publishWork(jobKey, failedJobRecord);
    }
  }

  private static boolean isJobRelatedIncident(final long jobKey) {
    return jobKey > 0;
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<IncidentRecord> record) {
    return SuspensionAware.bufferInternalOnly(record);
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<IncidentRecord> record) {
    return SuspensionAware.processInternalOnly(record);
  }
}
