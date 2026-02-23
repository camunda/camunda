/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocSubProcessUtils;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.ProcessingSession;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class JobCompleteProcessor implements TypedRecordProcessor<JobRecord> {

  private static final String TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE =
      """
          Task Listener job completion with variables payload provided is not yet supported \
          (job key '%d', type '%s', processInstanceKey '%d'). \
          Support will be enabled with the resolution of issue #23702.
          """;
  private static final String TL_JOB_COMPLETION_WITH_DENY_NOT_SUPPORTED_MESSAGE =
      """
          Denying result is not supported for '%s' task listener jobs \
          (job key '%d', type '%s', processInstanceKey '%d'). \
          Only the following listener event types support denying: %s.
          """;
  private static final String TL_JOB_COMPLETION_WITH_DENY_AND_CORRECTIONS_NOT_SUPPORTED_MESSAGE =
      """
          Expected to complete task listener job with corrections, but the job result is denied \
          (job key '%d', type '%s', processInstanceKey '%d'). \
          The corrections would be reverted by the denial. Either complete the job with corrections \
          without setting denied, or complete the job with a denied result but no corrections.
          """;
  private static final String TL_JOB_COMPLETION_WITH_UNKNOWN_CORRECTIONS_NOT_SUPPORTED_MESSAGE =
      """
          Expected to complete task listener job with a corrections result, but property '%s' \
          cannot be corrected (job key '%d', type '%s', processInstanceKey '%d'). \
          Only the following properties can be corrected: %s.
          """;
  private static final String
      TL_JOB_COMPLETION_WITH_ASSIGNEE_CORRECTION_ON_CREATING_NOT_SUPPORTED_MESSAGE =
          """
          Expected to complete task listener job, but correcting the assignee on 'CREATING' event is \
          not supported when the user task has an assignee defined in the model \
          (job key '%d', type '%s', processInstanceKey '%d'). \
          Use the 'CREATING' event to set an assignee only if it was not defined. \
          Use the 'ASSIGNING' event to correct an assignee that is already defined.
          """;
  private static final String MISSING_OR_INVALID_USER_TASK_KEY_FROM_ELEMENT_INSTANCE_MESSAGE =
      """
          Expected to retrieve a valid user task key from element instance, but either \
          the element instance was missing or the user task key was invalid \
          (elementInstanceKey: '%d', processInstanceKey: '%d').
          """;

  private static final String AHSP_JOB_NOT_ACTIVE =
      """
        Expected to complete ad-hoc sub-process job, but the ad-hoc sub-process instance is not active \
        (job key '%d', type '%s', processInstanceKey '%d')
      """;

  private static final Set<String> CORRECTABLE_PROPERTIES =
      Set.of(
          UserTaskRecord.ASSIGNEE,
          UserTaskRecord.CANDIDATE_GROUPS,
          UserTaskRecord.CANDIDATE_USERS,
          UserTaskRecord.DUE_DATE,
          UserTaskRecord.FOLLOW_UP_DATE,
          UserTaskRecord.PRIORITY);
  private static final Set<JobListenerEventType> LISTENER_EVENT_TYPES_THAT_SUPPORT_DENY =
      EnumSet.of(
          JobListenerEventType.ASSIGNING,
          JobListenerEventType.UPDATING,
          JobListenerEventType.COMPLETING);

  private final UserTaskState userTaskState;
  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final JobCommandPreconditionValidator preconditionChecker;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final JobProcessingMetrics jobMetrics;
  private final EventHandle eventHandle;
  private final ProcessingState processState;
  private final VariableBehavior variableBehavior;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobCompleteProcessor(
      final ProcessingState state,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final EventHandle eventHandle,
      final AuthorizationCheckBehavior authCheckBehavior,
      final VariableBehavior variableBehavior) {
    processState = state;
    userTaskState = state.getUserTaskState();
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    commandWriter = writers.command();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    preconditionChecker =
        new JobCommandPreconditionValidator(
            state.getJobState(),
            "complete",
            List.of(State.ACTIVATABLE, State.ACTIVATED),
            List.of(
                this::checkAdHocSubprocessActivationTargetsAreValid,
                this::checkAdHocSubprocessInstanceIsActive,
                this::checkAdHocSubProcessCompletionConditionNotFulfilledForElementActivation,
                this::checkTaskListenerJobForProvidingVariables,
                this::checkTaskListenerJobForSupportingDenying,
                this::checkTaskListenerJobForDenyingWithCorrections,
                this::checkCreatingListenerJobForAssigneeCorrection,
                this::checkTaskListenerJobForUnknownPropertyCorrections),
            authCheckBehavior);
    this.authCheckBehavior = authCheckBehavior;
    this.jobMetrics = jobMetrics;
    this.eventHandle = eventHandle;
    this.variableBehavior = variableBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record, final ProcessingSession session) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, record)
        .flatMap(job -> checkAuthorization(record, job))
        .ifRightOrLeft(
            job -> completeJob(record, job, session),
            rejection -> {
              rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(record, rejection.type(), rejection.reason());
            });
  }

  private void completeJob(
      final TypedRecord<JobRecord> command, final JobRecord job, final ProcessingSession session) {

    preCompleteActions(job, session);

    job.setVariables(command.getValue().getVariablesBuffer());
    job.setResult(command.getValue().getResult());

    stateWriter.appendFollowUpEvent(command.getKey(), JobIntent.COMPLETED, job);
    responseWriter.writeEventOnCommand(command.getKey(), JobIntent.COMPLETED, job, command);

    jobMetrics.countJobEvent(JobAction.COMPLETED, job.getJobKind(), job.getType());

    postCompleteActions(job);
  }

  private void preCompleteActions(final JobRecord job, final ProcessingSession session) {
    if (job.isAgentic()) {
      session.appendAgentInfoToFollowUps(new AgentInfo().setElementId(job.getElementId()));
    }
  }

  private void postCompleteActions(final JobRecord value) {

    final var elementInstanceKey = value.getElementInstanceKey();
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return;
    }

    switch (value.getJobKind()) {
      case EXECUTION_LISTENER -> {
        // to store the variable for merge, to handle concurrent commands
        eventHandle.triggeringProcessEvent(value);

        commandWriter.appendFollowUpCommand(
            elementInstanceKey,
            ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER,
            elementInstance.getValue());
      }
      case TASK_LISTENER -> {
        /*
         We retrieve the intermediate user task state rather than the regular user task record
         because the intermediate state captures the exact data provided during the original
         user task command (e.g., COMPLETE, ASSIGN). This data includes variables, actions,
         and other command-related details that may not yet be reflected in the persisted user
         task record, that can be accessed via `userTaskState.getUserTask`.

         When task listeners are involved, it's essential to preserve this original state
         until all task listeners have been executed. Retrieving the intermediate state here
         ensures that the finalization of the user task command uses the correct, unmodified
         data as originally intended by the user. Once all task listeners have been processed
         and the original user task command is finalized, the intermediate state is cleared.
        */
        final var userTask =
            userTaskState.getIntermediateState(elementInstance.getUserTaskKey()).getRecord();

        if (value.getResult().isDenied()) {
          userTask.setDeniedReason(value.getResult().getDeniedReason());
          commandWriter.appendFollowUpCommand(
              userTask.getUserTaskKey(), UserTaskIntent.DENY_TASK_LISTENER, userTask);
        } else {
          userTask.correctAttributes(
              value.getResult().getCorrectedAttributes(), value.getResult().getCorrections());
          commandWriter.appendFollowUpCommand(
              userTask.getUserTaskKey(), UserTaskIntent.COMPLETE_TASK_LISTENER, userTask);
        }
      }
      case AD_HOC_SUB_PROCESS -> handleAdHocSubProcessJob(commandWriter, value, elementInstance);
      default -> {
        final long scopeKey = elementInstance.getValue().getFlowScopeKey();
        final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

        if (scopeInstance != null && scopeInstance.isActive()) {
          eventHandle.triggeringProcessEvent(value);
          commandWriter.appendFollowUpCommand(
              elementInstanceKey,
              ProcessInstanceIntent.COMPLETE_ELEMENT,
              elementInstance.getValue());
        }
      }
    }
  }

  private void handleAdHocSubProcessJob(
      final TypedCommandWriter commandWriter,
      final JobRecord jobRecord,
      final ElementInstance adHocSubProcessInstance) {

    final var jobResult = jobRecord.getResult();

    final AdHocSubProcessInstructionRecord instructionRecord =
        new AdHocSubProcessInstructionRecord()
            .setAdHocSubProcessInstanceKey(jobRecord.getElementInstanceKey())
            .setCompletionConditionFulfilled(jobResult.isCompletionConditionFulfilled())
            .setCancelRemainingInstances(jobResult.isCancelRemainingInstances());

    if (!jobResult.getActivateElements().isEmpty()) {
      jobResult.getActivateElements().stream()
          .map(JobResultActivateElement.class::cast)
          .forEach(
              element ->
                  instructionRecord
                      .activateElements()
                      .add()
                      .setElementId(element.getElementId())
                      .setVariables(element.getVariablesBuffer()));

      commandWriter.appendFollowUpCommand(
          jobRecord.getElementInstanceKey(),
          AdHocSubProcessInstructionIntent.ACTIVATE,
          instructionRecord);
    }

    if (jobResult.isCompletionConditionFulfilled()) {
      commandWriter.appendFollowUpCommand(
          jobRecord.getElementInstanceKey(),
          AdHocSubProcessInstructionIntent.COMPLETE,
          instructionRecord);
    }

    if (jobRecord.getVariablesBuffer().capacity() > 0) {
      propagateJobVariablesToAdHocSubProcess(jobRecord, adHocSubProcessInstance);
    }
  }

  private void propagateJobVariablesToAdHocSubProcess(
      final JobRecord completingJobRecord, final ElementInstance targetAdHocSubProcess) {
    final var targetAdHocSubProcessInstanceValue = targetAdHocSubProcess.getValue();

    variableBehavior.mergeDocument(
        targetAdHocSubProcess.getKey(),
        targetAdHocSubProcessInstanceValue.getProcessDefinitionKey(),
        targetAdHocSubProcessInstanceValue.getProcessInstanceKey(),
        targetAdHocSubProcessInstanceValue.getRootProcessInstanceKey(),
        targetAdHocSubProcessInstanceValue.getBpmnProcessIdBuffer(),
        targetAdHocSubProcessInstanceValue.getTenantId(),
        completingJobRecord.getVariablesBuffer());
  }

  private Either<Rejection, JobRecord>
      checkAdHocSubProcessCompletionConditionNotFulfilledForElementActivation(
          final TypedRecord<JobRecord> command, final JobRecord job) {

    if (job.getJobKind() == JobKind.AD_HOC_SUB_PROCESS) {
      final List<String> elementsToBeActivated =
          command.getValue().getResult().getActivateElements().stream()
              .map(JobResultActivateElementValue::getElementId)
              .toList();

      return AdHocSubProcessUtils
          .validateThatCompletionConditionIsNotFulfilledWhenActivatingElements(
              job.getElementInstanceKey(),
              command.getValue().getResult().isCompletionConditionFulfilled(),
              elementsToBeActivated)
          .map(right -> job);
    }
    return Either.right(job);
  }

  private Either<Rejection, JobRecord> checkAdHocSubprocessActivationTargetsAreValid(
      final TypedRecord<JobRecord> command, final JobRecord job) {

    if (job.getJobKind() == JobKind.AD_HOC_SUB_PROCESS) {
      final ExecutableAdHocSubProcess executableAdHocSubProcess =
          processState
              .getProcessState()
              .getProcessByKeyAndTenant(job.getProcessDefinitionKey(), job.getTenantId())
              .getProcess()
              .getElementById(job.getElementId(), ExecutableAdHocSubProcess.class);

      final List<String> elementsToBeActivated =
          command.getValue().getResult().getActivateElements().stream()
              .map(JobResultActivateElementValue::getElementId)
              .toList();

      return AdHocSubProcessUtils.validateActivateElementsExistInAdHocSubProcess(
              job.getElementInstanceKey(), executableAdHocSubProcess, elementsToBeActivated)
          .map(right -> job);
    }
    return Either.right(job);
  }

  private Either<Rejection, JobRecord> checkAdHocSubprocessInstanceIsActive(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() == JobKind.AD_HOC_SUB_PROCESS) {
      final var adHocSubProcessElementInstance =
          elementInstanceState.getInstance(job.getElementInstanceKey());
      if (adHocSubProcessElementInstance != null && !adHocSubProcessElementInstance.isActive()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                AHSP_JOB_NOT_ACTIVE.formatted(
                    command.getKey(), job.getType(), job.getProcessInstanceKey())));
      }
    }

    return Either.right(job);
  }

  /** We currently don't support completing task listener jobs with variables. */
  private Either<Rejection, JobRecord> checkTaskListenerJobForProvidingVariables(
      final TypedRecord<JobRecord> command, final JobRecord job) {

    if (job.getJobKind() == JobKind.TASK_LISTENER && hasVariables(command)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE.formatted(
                  command.getKey(), job.getType(), job.getProcessInstanceKey())));
    }

    return Either.right(job);
  }

  private Either<Rejection, JobRecord> checkTaskListenerJobForSupportingDenying(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    final boolean denied = command.getValue().getResult().isDenied();
    final var listenerEventType = job.getJobListenerEventType();

    if (denied && !LISTENER_EVENT_TYPES_THAT_SUPPORT_DENY.contains(listenerEventType)) {
      final var supportedEventTypes =
          LISTENER_EVENT_TYPES_THAT_SUPPORT_DENY.stream()
              .map(JobListenerEventType::name)
              .sorted()
              .collect(Collectors.joining(", ", "[", "]"));

      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              TL_JOB_COMPLETION_WITH_DENY_NOT_SUPPORTED_MESSAGE.formatted(
                  listenerEventType,
                  command.getKey(),
                  job.getType(),
                  job.getProcessInstanceKey(),
                  supportedEventTypes)));
    }

    return Either.right(job);
  }

  private Either<Rejection, JobRecord> checkTaskListenerJobForDenyingWithCorrections(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    final var jobResult = command.getValue().getResult();
    if (jobResult.isDenied() && !jobResult.getCorrectedAttributes().isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              TL_JOB_COMPLETION_WITH_DENY_AND_CORRECTIONS_NOT_SUPPORTED_MESSAGE.formatted(
                  command.getKey(), job.getType(), job.getProcessInstanceKey())));
    } else {
      return Either.right(job);
    }
  }

  private Either<Rejection, JobRecord> checkCreatingListenerJobForAssigneeCorrection(
      final TypedRecord<JobRecord> command, final JobRecord job) {

    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    if (job.getJobListenerEventType() != JobListenerEventType.CREATING) {
      return Either.right(job);
    }

    final var correctedAttributes = command.getValue().getResult().getCorrectedAttributes();

    if (correctedAttributes.contains(UserTaskRecord.ASSIGNEE)) {
      final var uerTaskKey = getUserTaskKey(job);
      final var initialAssignee = userTaskState.findInitialAssignee(uerTaskKey);

      if (initialAssignee.isPresent()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                TL_JOB_COMPLETION_WITH_ASSIGNEE_CORRECTION_ON_CREATING_NOT_SUPPORTED_MESSAGE
                    .formatted(command.getKey(), job.getType(), job.getProcessInstanceKey())));
      }
    }

    return Either.right(job);
  }

  private Either<Rejection, JobRecord> checkTaskListenerJobForUnknownPropertyCorrections(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    final var optionalUnknownProperty =
        command.getValue().getResult().getCorrectedAttributes().stream()
            .filter(Predicate.not(CORRECTABLE_PROPERTIES::contains))
            .findAny();

    if (optionalUnknownProperty.isEmpty()) {
      return Either.right(job);
    }

    final var correctableProperties =
        CORRECTABLE_PROPERTIES.stream().sorted().collect(Collectors.joining(", ", "[", "]"));
    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            TL_JOB_COMPLETION_WITH_UNKNOWN_CORRECTIONS_NOT_SUPPORTED_MESSAGE.formatted(
                optionalUnknownProperty.get(),
                command.getKey(),
                job.getType(),
                job.getProcessInstanceKey(),
                correctableProperties)));
  }

  private boolean hasVariables(final TypedRecord<JobRecord> command) {
    return !DocumentValue.EMPTY_DOCUMENT.equals(command.getValue().getVariablesBuffer());
  }

  private long getUserTaskKey(final JobRecord job) {
    return Optional.ofNullable(elementInstanceState.getInstance(job.getElementInstanceKey()))
        .map(ElementInstance::getUserTaskKey)
        .filter(key -> key > 0)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    MISSING_OR_INVALID_USER_TASK_KEY_FROM_ELEMENT_INSTANCE_MESSAGE.formatted(
                        job.getElementInstanceKey(), job.getProcessInstanceKey())));
  }

  private Either<Rejection, JobRecord> checkAuthorization(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(job.getTenantId())
            .addResourceId(job.getBpmnProcessId())
            .build();
    return authCheckBehavior.isAuthorizedOrInternalCommand(request).map(unused -> job);
  }
}
