/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebePriorityDefinitionValidator.PRIORITY_LOWER_BOUND;
import static io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebePriorityDefinitionValidator.PRIORITY_UPPER_BOUND;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePriorityDefinition;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BpmnUserTaskBehavior {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BpmnUserTaskBehavior.class.getPackageName());
  private static final Set<LifecycleState> CANCELABLE_LIFECYCLE_STATES =
      EnumSet.complementOf(EnumSet.of(LifecycleState.NOT_FOUND, LifecycleState.CANCELING));

  private final HeaderEncoder headerEncoder = new HeaderEncoder(LOGGER);
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final FormState formState;
  private final UserTaskState userTaskState;
  private final VariableState variableState;
  private final AsyncRequestState asyncRequestState;
  private final InstantSource clock;

  public BpmnUserTaskBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionProcessor expressionBehavior,
      final BpmnStateBehavior stateBehavior,
      final FormState formState,
      final UserTaskState userTaskState,
      final VariableState variableState,
      final AsyncRequestState asyncRequestState,
      final InstantSource clock) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.expressionBehavior = expressionBehavior;
    this.stateBehavior = stateBehavior;
    this.formState = formState;
    this.userTaskState = userTaskState;
    this.variableState = variableState;
    this.asyncRequestState = asyncRequestState;
    this.clock = clock;
  }

  public Either<Failure, UserTaskProperties> evaluateUserTaskExpressions(
      final ExecutableUserTask element, final BpmnElementContext context) {
    final var userTaskProps = element.getUserTaskProperties();
    final var scopeKey = context.getElementInstanceKey();
    return Either.<Failure, UserTaskProperties>right(new UserTaskProperties())
        .flatMap(
            p -> evaluateAssigneeExpression(userTaskProps.getAssignee(), scopeKey).map(p::assignee))
        .flatMap(
            p ->
                evaluateCandidateGroupsExpression(userTaskProps.getCandidateGroups(), scopeKey)
                    .map(p::candidateGroups))
        .flatMap(
            p ->
                evaluateCandidateUsersExpression(userTaskProps.getCandidateUsers(), scopeKey)
                    .map(p::candidateUsers))
        .flatMap(p -> evaluateDateExpression(userTaskProps.getDueDate(), scopeKey).map(p::dueDate))
        .flatMap(
            p ->
                evaluateDateExpression(userTaskProps.getFollowUpDate(), scopeKey)
                    .map(p::followUpDate))
        .flatMap(
            p ->
                evaluateFormIdExpressionToFormKey(
                        userTaskProps.getFormId(),
                        userTaskProps.getFormBindingType(),
                        userTaskProps.getFormVersionTag(),
                        context,
                        scopeKey)
                    .map(p::formKey))
        .flatMap(
            p ->
                evaluateExternalFormReferenceExpression(
                        userTaskProps.getExternalFormReference(), scopeKey)
                    .map(p::externalFormReference))
        .flatMap(
            p ->
                evaluatePriorityExpression(userTaskProps.getPriority(), scopeKey).map(p::priority));
  }

  public UserTaskRecord createNewUserTask(
      final BpmnElementContext context,
      final ExecutableUserTask element,
      final UserTaskProperties userTaskProperties) {
    final var userTaskKey = keyGenerator.nextKey();
    return createNewUserTask(
        userTaskKey,
        context,
        element.getUserTaskProperties().getTaskHeaders(),
        element.getId(),
        userTaskProperties);
  }

  public UserTaskRecord createNewUserTask(
      final long userTaskKey,
      final BpmnElementContext context,
      final Map<String, String> taskHeaders,
      final DirectBuffer elementId,
      final UserTaskProperties userTaskProperties) {

    final var encodedHeaders = headerEncoder.encode(taskHeaders);

    final var userTaskRecord =
        new UserTaskRecord()
            .setVariables(DocumentValue.EMPTY_DOCUMENT)
            .setUserTaskKey(userTaskKey)
            .setAssignee(userTaskProperties.getAssignee())
            .setCandidateGroupsList(userTaskProperties.getCandidateGroups())
            .setCandidateUsersList(userTaskProperties.getCandidateUsers())
            .setDueDate(userTaskProperties.getDueDate())
            .setFollowUpDate(userTaskProperties.getFollowUpDate())
            .setFormKey(userTaskProperties.getFormKey())
            .setExternalFormReference(userTaskProperties.getExternalFormReference())
            .setCustomHeaders(encodedHeaders)
            .setBpmnProcessId(context.getBpmnProcessId())
            .setProcessDefinitionVersion(context.getProcessVersion())
            .setProcessDefinitionKey(context.getProcessDefinitionKey())
            .setProcessInstanceKey(context.getProcessInstanceKey())
            .setElementId(elementId)
            .setElementInstanceKey(context.getElementInstanceKey())
            .setTenantId(context.getTenantId())
            .setPriority(userTaskProperties.getPriority())
            .setCreationTimestamp(clock.millis());

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    return userTaskRecord;
  }

  public Either<Failure, String> evaluateAssigneeExpression(
      final Expression assignee, final long scopeKey) {
    if (assignee == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(assignee, scopeKey);
  }

  public Either<Failure, List<String>> evaluateCandidateGroupsExpression(
      final Expression candidateGroups, final long scopeKey) {
    if (candidateGroups == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateArrayOfStringsExpression(candidateGroups, scopeKey);
  }

  public Either<Failure, List<String>> evaluateCandidateUsersExpression(
      final Expression candidateUsers, final long scopeKey) {
    if (candidateUsers == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateArrayOfStringsExpression(candidateUsers, scopeKey);
  }

  public Either<Failure, String> evaluateDateExpression(
      final Expression date, final long scopeKey) {
    if (date == null) {
      return Either.right(null);
    }
    return expressionBehavior
        .evaluateDateTimeExpression(date, scopeKey, true)
        .map(optionalDate -> optionalDate.map(ZonedDateTime::toString).orElse(null));
  }

  public Either<Failure, Long> evaluateFormIdExpressionToFormKey(
      final Expression formIdExpression,
      final ZeebeBindingType bindingType,
      final String versionTag,
      final BpmnElementContext context,
      final long scopeKey) {
    if (formIdExpression == null) {
      return Either.right(null);
    }
    return expressionBehavior
        .evaluateStringExpression(formIdExpression, scopeKey)
        .flatMap(
            formId -> {
              final var form = findLinkedForm(formId, bindingType, versionTag, context, scopeKey);
              return form.map(PersistedForm::getFormKey);
            });
  }

  private Either<Failure, PersistedForm> findLinkedForm(
      final String formId,
      final ZeebeBindingType bindingType,
      final String versionTag,
      final BpmnElementContext context,
      final long scopeKey) {
    return switch (bindingType) {
      case deployment -> findFormByIdInSameDeployment(formId, context, scopeKey);
      case latest -> findLatestFormById(formId, context.getTenantId(), scopeKey);
      case versionTag ->
          findFormByIdAndVersionTag(formId, versionTag, context.getTenantId(), scopeKey);
    };
  }

  private Either<Failure, PersistedForm> findFormByIdInSameDeployment(
      final String formId, final BpmnElementContext context, final long scopeKey) {
    return stateBehavior
        .getDeploymentKey(context.getProcessDefinitionKey(), context.getTenantId())
        .flatMap(
            deploymentKey ->
                formState
                    .findFormByIdAndDeploymentKey(formId, deploymentKey, context.getTenantId())
                    .<Either<Failure, PersistedForm>>map(Either::right)
                    .orElseGet(
                        () ->
                            Either.left(
                                new Failure(
                                    String.format(
                                        """
                                        Expected to use a form with id '%s' with binding type 'deployment', \
                                        but no such form found in the deployment with key %s which contained the current process. \
                                        To resolve this incident, migrate the process instance to a process definition \
                                        that is deployed together with the intended form to use.\
                                        """,
                                        formId, deploymentKey),
                                    ErrorType.FORM_NOT_FOUND,
                                    scopeKey))));
  }

  private Either<Failure, PersistedForm> findLatestFormById(
      final String formId, final String tenantId, final long scopeKey) {
    return formState
        .findLatestFormById(formId, tenantId)
        .<Either<Failure, PersistedForm>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new Failure(
                        String.format(
                            "Expected to find a form with id '%s',"
                                + " but no form with this id is found,"
                                + " at least a form with this id should be available."
                                + " To resolve the Incident please deploy a form with the same id",
                            formId),
                        ErrorType.FORM_NOT_FOUND,
                        scopeKey)));
  }

  private Either<Failure, PersistedForm> findFormByIdAndVersionTag(
      final String formId, final String versionTag, final String tenantId, final long scopeKey) {
    return formState
        .findFormByIdAndVersionTag(formId, versionTag, tenantId)
        .<Either<Failure, PersistedForm>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new Failure(
                        String.format(
                            """
                            Expected to use a form with id '%s' and version tag '%s', but no such form found. \
                            To resolve the incident, deploy a form with the given id and version tag.
                            """,
                            formId, versionTag),
                        ErrorType.FORM_NOT_FOUND,
                        scopeKey)));
  }

  public Either<Failure, String> evaluateExternalFormReferenceExpression(
      final Expression externalFormReference, final long scopeKey) {
    if (externalFormReference == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(externalFormReference, scopeKey);
  }

  public Either<Failure, Integer> evaluatePriorityExpression(
      final Expression priorityExpression, final long scopeKey) {
    if (priorityExpression == null) {
      return Either.right(ZeebePriorityDefinition.DEFAULT_NUMBER_PRIORITY);
    }
    return expressionBehavior
        .evaluateIntegerExpression(priorityExpression, scopeKey)
        .flatMap(
            priority -> {
              if (priority < PRIORITY_LOWER_BOUND || priority > PRIORITY_UPPER_BOUND) {
                return Either.left(
                    new Failure(
                        String.format(
                            "Expected priority to be in the range [0,100] but was '%s'.", priority),
                        ErrorType.EXTRACT_VALUE_ERROR,
                        scopeKey));
              }
              return Either.right(priority);
            });
  }

  public void cancelUserTask(final ElementInstance elementInstance) {
    userTaskCanceling(elementInstance).ifPresent(this::userTaskCanceled);
  }

  public void cancelUserTask(final BpmnElementContext elementInstanceContext) {
    final var elementInstance = stateBehavior.getElementInstance(elementInstanceContext);
    cancelUserTask(elementInstance);
  }

  public Optional<UserTaskRecord> userTaskCanceling(final ElementInstance elementInstance) {
    final long userTaskKey = elementInstance.getUserTaskKey();
    if (userTaskKey <= 0) {
      return Optional.empty();
    }
    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);
    if (!CANCELABLE_LIFECYCLE_STATES.contains(lifecycleState)) {
      return Optional.empty();
    }
    final UserTaskRecord userTask = userTaskState.getUserTask(userTaskKey);
    if (userTask == null) {
      return Optional.empty();
    }

    rejectOngoingRequestsForUserTaskBeforeCancellation(userTask);

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CANCELING, userTask);
    return Optional.of(userTask);
  }

  private void rejectOngoingRequestsForUserTaskBeforeCancellation(final UserTaskRecord userTask) {
    final long userTaskElementInstanceKey = userTask.getElementInstanceKey();
    asyncRequestState
        .findAllRequestsByScopeKey(userTaskElementInstanceKey)
        .forEach(
            request -> {
              switch (request.valueType()) {
                case USER_TASK ->
                    responseWriter.writeRejection(
                        userTask.getUserTaskKey(),
                        request.intent(),
                        userTask,
                        ValueType.USER_TASK,
                        RejectionType.INVALID_STATE,
                        "user task was canceled during ongoing transition",
                        request.requestId(),
                        request.requestStreamId());
                case VARIABLE_DOCUMENT ->
                    variableState
                        .findVariableDocumentState(userTaskElementInstanceKey)
                        .ifPresent(
                            variableDocument -> {
                              responseWriter.writeRejection(
                                  variableDocument.getKey(),
                                  request.intent(),
                                  variableDocument.getRecord(),
                                  ValueType.VARIABLE_DOCUMENT,
                                  RejectionType.INVALID_STATE,
                                  "user task was canceled during handling task variables update",
                                  request.requestId(),
                                  request.requestStreamId());
                            });
                default ->
                    LOGGER.debug(
                        "No rejection logic implemented for ongoing request with valueType={} and intent={} "
                            + "triggered against user task (key={}, elementInstanceKey={}). "
                            + "Async request metadata will be cleaned up, but the request will not be explicitly rejected.",
                        request.valueType(),
                        request.intent(),
                        userTask.getUserTaskKey(),
                        userTaskElementInstanceKey);
              }

              stateWriter.appendFollowUpEvent(
                  request.requestId(), AsyncRequestIntent.PROCESSED, request.record());
            });
  }

  public void userTaskCanceled(final UserTaskRecord userTaskRecord) {
    final long userTaskKey = userTaskRecord.getUserTaskKey();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CANCELED, userTaskRecord);
  }

  public void userTaskCreated(final UserTaskRecord userTaskRecord) {
    final long userTaskKey = userTaskRecord.getUserTaskKey();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
  }

  public void userTaskAssigning(final UserTaskRecord userTaskRecord, final String assignee) {
    final long userTaskKey = userTaskRecord.getUserTaskKey();
    if (!userTaskRecord.getAssignee().equals(assignee)) {
      userTaskRecord.setAssignee(assignee);
      userTaskRecord.setAssigneeChanged();
    }
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);
  }

  public void userTaskAssigned(final UserTaskRecord userTaskRecord, final String assignee) {
    final long userTaskKey = userTaskRecord.getUserTaskKey();
    userTaskRecord.setAssignee(assignee);
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
  }

  public static final class UserTaskProperties {

    private String assignee;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private String dueDate;
    private String externalFormReference;
    private String followUpDate;
    private Long formKey;
    private Integer priority;

    public String getAssignee() {
      return getOrEmpty(assignee);
    }

    public UserTaskProperties assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    public List<String> getCandidateGroups() {
      return getOrEmpty(candidateGroups);
    }

    public UserTaskProperties candidateGroups(final List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    public List<String> getCandidateUsers() {
      return getOrEmpty(candidateUsers);
    }

    public UserTaskProperties candidateUsers(final List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    public String getDueDate() {
      return getOrEmpty(dueDate);
    }

    public UserTaskProperties dueDate(final String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public String getExternalFormReference() {
      return getOrEmpty(externalFormReference);
    }

    public UserTaskProperties externalFormReference(final String externalFormReference) {
      this.externalFormReference = externalFormReference;
      return this;
    }

    public String getFollowUpDate() {
      return getOrEmpty(followUpDate);
    }

    public UserTaskProperties followUpDate(final String followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    public Long getFormKey() {
      return formKey == null ? -1 : formKey;
    }

    public UserTaskProperties formKey(final Long formKey) {
      this.formKey = formKey;
      return this;
    }

    public Integer getPriority() {
      return priority == null ? ZeebePriorityDefinition.DEFAULT_NUMBER_PRIORITY : priority;
    }

    public UserTaskProperties priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    private static String getOrEmpty(final String stringValue) {
      return stringValue == null ? "" : stringValue;
    }

    private static List<String> getOrEmpty(final List<String> listValue) {
      return listValue == null ? Collections.emptyList() : listValue;
    }
  }
}
